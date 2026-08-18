package com.takehome.forms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.takehome.forms.ingest.IngestedForm;
import com.takehome.forms.ingest.IngestedFormValidator;
import com.takehome.forms.ingest.ValidationResult;
import com.takehome.forms.providers.EmailClient;
import com.takehome.forms.providers.EmailRequest;
import com.takehome.forms.providers.GeocodingCoordinates;
import com.takehome.forms.providers.HttpResponse;
import com.takehome.forms.providers.PostcodeLookupClient;
import com.takehome.forms.submission.EmailStatus;
import com.takehome.forms.submission.OutboxEmail;
import com.takehome.forms.submission.OutboxEmailRepository;
import com.takehome.forms.submission.Submission;
import com.takehome.forms.submission.SubmissionRepository;
import com.takehome.forms.submission.SubmissionStatus;
import com.takehome.forms.submission.TransformedFormRepository;
import com.takehome.forms.transform.FormTransformer;
import com.takehome.forms.transform.TransformResult;
import com.takehome.forms.transform.TransformedForm;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class IngestionService {

	// Log identifiers and error reasons only — never the raw payload or transformed fields
	// (name, email, DOB, address). This is healthcare data; it doesn't belong in plaintext logs.
	private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

	private final IngestedFormValidator validator;
	private final PostcodeLookupClient postcodeLookupClient;
	private final FormTransformer transformer;
	private final EmailClient emailClient;
	private final SubmissionRepository submissionRepository;
	private final TransformedFormRepository transformedFormRepository;
	private final OutboxEmailRepository outboxEmailRepository;
	private final ObjectMapper objectMapper;
	private final FormsMetrics metrics;
	private final TransactionTemplate transactionTemplate;

	public IngestionService(
			IngestedFormValidator validator,
			PostcodeLookupClient postcodeLookupClient,
			FormTransformer transformer,
			EmailClient emailClient,
			SubmissionRepository submissionRepository,
			TransformedFormRepository transformedFormRepository,
			OutboxEmailRepository outboxEmailRepository,
			ObjectMapper objectMapper,
			FormsMetrics metrics,
			PlatformTransactionManager transactionManager
	) {
		this.validator = validator;
		this.postcodeLookupClient = postcodeLookupClient;
		this.transformer = transformer;
		this.emailClient = emailClient;
		this.submissionRepository = submissionRepository;
		this.transformedFormRepository = transformedFormRepository;
		this.outboxEmailRepository = outboxEmailRepository;
		this.objectMapper = objectMapper;
		this.metrics = metrics;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	public IngestOutcome ingest(JsonNode payload) {
		String sessionId = payload.path("session_id").asText(null);
		String applicationReference = payload.path("application_reference").asText(null);

		if (isBlank(sessionId) || isBlank(applicationReference)) {
			// Both columns are NOT NULL on submissions — nowhere to persist this, so it's
			// malformed rather than a processable-but-invalid submission.
			log.warn("Rejected malformed /ingest request: missing session_id or application_reference");
			return new IngestOutcome.MalformedRequest("session_id and application_reference are required");
		}

		Submission submission = submissionRepository.findOrCreate(sessionId, applicationReference, payload.toString());

		if (submission.status() != SubmissionStatus.RECEIVED) {
			// Already processed by an earlier delivery of this session_id — don't reprocess.
			log.info("Duplicate delivery for session_id={}, already {}", sessionId, submission.status());
			return new IngestOutcome.Processed(submission.status(), submission.lastError());
		}

		log.info("Ingesting submission {} (session_id={})", submission.id(), sessionId);
		return process(submission, payload);
	}

	private IngestOutcome process(Submission submission, JsonNode payload) {
		ValidationResult validation = validator.validate(payload);
		if (validation instanceof ValidationResult.Invalid invalid) {
			String error = String.join("; ", invalid.errors());
			log.warn("Submission {} failed schema validation: {}", submission.id(), error);
			submissionRepository.updateStatus(submission.id(), SubmissionStatus.SCHEMA_INVALID, error);
			metrics.recordSubmissionOutcome(SubmissionStatus.SCHEMA_INVALID);
			return new IngestOutcome.Processed(SubmissionStatus.SCHEMA_INVALID, error);
		}
		IngestedForm form = ((ValidationResult.Valid) validation).form();

		HttpResponse<GeocodingCoordinates> geocodeResponse = postcodeLookupClient.lookupPostcode(form.address().postcode());
		if (geocodeResponse.statusCode() != 200 || geocodeResponse.body() == null) {
			String error = "postcode lookup failed with status " + geocodeResponse.statusCode();
			log.warn("Submission {} failed geocoding: {}", submission.id(), error);
			submissionRepository.updateStatus(submission.id(), SubmissionStatus.GEOCODE_FAILED, error);
			metrics.recordSubmissionOutcome(SubmissionStatus.GEOCODE_FAILED);
			return new IngestOutcome.Processed(SubmissionStatus.GEOCODE_FAILED, error);
		}

		TransformResult transformResult = transformer.transform(form, geocodeResponse.body());
		if (transformResult instanceof TransformResult.Failure failure) {
			log.warn("Submission {} failed transform: {}", submission.id(), failure.reason());
			submissionRepository.updateStatus(submission.id(), SubmissionStatus.TRANSFORM_FAILED, failure.reason());
			metrics.recordSubmissionOutcome(SubmissionStatus.TRANSFORM_FAILED);
			return new IngestOutcome.Processed(SubmissionStatus.TRANSFORM_FAILED, failure.reason());
		}
		TransformedForm transformed = ((TransformResult.Success) transformResult).form();

		SubmissionStatus finalStatus = persistTransformed(submission.id(), transformed);
		log.info("Submission {} reached {}", submission.id(), finalStatus);
		metrics.recordSubmissionOutcome(finalStatus);
		sendNotificationEmail(submission.id());
		return new IngestOutcome.Processed(finalStatus, null);
	}

	// TransactionTemplate, not @Transactional — a same-class call bypasses Spring's proxy and
	// would silently drop the transaction. All three writes commit or none do.
	private SubmissionStatus persistTransformed(long submissionId, TransformedForm transformed) {
		return transactionTemplate.execute(status -> {
			boolean inserted = transformedFormRepository.insertUnlessApplicationAlreadyTransformed(submissionId, transformed);
			SubmissionStatus finalStatus = inserted ? SubmissionStatus.READY : SubmissionStatus.DUPLICATE_APPLICATION;
			submissionRepository.updateStatus(submissionId, finalStatus, null);
			outboxEmailRepository.insertPending(submissionId);
			return finalStatus;
		});
	}

	// Runs after commit — email is external I/O, never inside a DB transaction. If this fails,
	// the row stays PENDING/FAILED for /retry to pick up later.
	private void sendNotificationEmail(long submissionId) {
		OutboxEmail email = outboxEmailRepository.findBySubmissionId(submissionId).orElseThrow();
		HttpResponse<Void> response = emailClient.sendEmail(new EmailRequest(
				"happyforms@bots.com",
				"noreply@ourservice.com",
				"Form ingested",
				"A form was ingested (submission " + submissionId + ")"
		));
		if (response.statusCode() == 200) {
			log.info("Notification email sent for submission {}", submissionId);
			outboxEmailRepository.markSent(email.id());
			metrics.recordEmailOutcome(EmailStatus.SENT);
		} else {
			String error = "email send failed with status " + response.statusCode();
			log.warn("Notification email failed for submission {}: {}", submissionId, error);
			outboxEmailRepository.markFailed(email.id(), error);
			metrics.recordEmailOutcome(EmailStatus.FAILED);
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	public RetrySummary retryAll() {
		Timer.Sample sample = metrics.startRetrySweepTimer();
		RetrySummary summary = new RetrySummary(retrySubmissions(), retryOutboxEmails());
		metrics.stopRetrySweepTimer(sample);
		log.info("Retry sweep: {} submissions retried ({} succeeded, {} failed), {} emails retried",
				summary.submissions().size(), summary.submissionsSucceeded(), summary.submissionsFailed(),
				summary.emailsRetried());
		return summary;
	}

	// Re-runs the same process() pipeline against the stored raw_payload — a submission that
	// now succeeds (e.g. after a code fix) goes through persistTransformed()/outbox exactly as
	// it would have on first ingest. Each submission is isolated in its own try/catch — one
	// failing (e.g. a rare concurrent-retry race) must not abort the sweep for the rest.
	private List<RetryItemResult> retrySubmissions() {
		List<Submission> needingRetry = submissionRepository.findNeedingRetry();
		List<RetryItemResult> results = new ArrayList<>();
		for (Submission submission : needingRetry) {
			submissionRepository.incrementRetryCount(submission.id());
			try {
				IngestOutcome.Processed outcome = (IngestOutcome.Processed)
						process(submission, objectMapper.readTree(submission.rawPayload()));
				boolean succeeded = outcome.status() == SubmissionStatus.READY
						|| outcome.status() == SubmissionStatus.DUPLICATE_APPLICATION;
				results.add(new RetryItemResult(submission.id(), submission.applicationReference(),
						succeeded, outcome.status().name(), outcome.error()));
			} catch (Exception e) {
				log.error("Retry failed for submission {}, continuing with the rest of the sweep",
						submission.id(), e);
				results.add(new RetryItemResult(submission.id(), submission.applicationReference(),
						false, "ERROR", e.getMessage()));
			}
		}
		return results;
	}

	// Same isolation as retrySubmissions() — one email failing to (re)send must not stop the
	// rest of the sweep from being attempted.
	private int retryOutboxEmails() {
		List<OutboxEmail> undelivered = outboxEmailRepository.findUndelivered();
		for (OutboxEmail email : undelivered) {
			try {
				sendNotificationEmail(email.submissionId());
			} catch (Exception e) {
				log.error("Retry failed for outbox email of submission {}, continuing with the rest of the sweep",
						email.submissionId(), e);
			}
		}
		return undelivered.size();
	}
}
