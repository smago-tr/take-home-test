package com.takehome.forms;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import com.takehome.forms.submission.OutboxEmail;
import com.takehome.forms.submission.OutboxEmailRepository;
import com.takehome.forms.submission.Submission;
import com.takehome.forms.submission.SubmissionRepository;
import com.takehome.forms.submission.SubmissionStatus;
import com.takehome.forms.submission.TransformedFormRepository;
import com.takehome.forms.transform.FormTransformer;
import com.takehome.forms.transform.TransformResult;
import com.takehome.forms.transform.TransformedForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Service
public class IngestionService {

	private final IngestedFormValidator validator;
	private final PostcodeLookupClient postcodeLookupClient;
	private final FormTransformer transformer;
	private final EmailClient emailClient;
	private final SubmissionRepository submissionRepository;
	private final TransformedFormRepository transformedFormRepository;
	private final OutboxEmailRepository outboxEmailRepository;
	private final ObjectMapper objectMapper;
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
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	public IngestOutcome ingest(JsonNode payload) {
		String sessionId = payload.path("session_id").asText(null);
		String applicationReference = payload.path("application_reference").asText(null);

		if (isBlank(sessionId) || isBlank(applicationReference)) {
			// Both columns are NOT NULL on submissions — nowhere to persist this, so it's
			// malformed rather than a processable-but-invalid submission.
			return new IngestOutcome.MalformedRequest("session_id and application_reference are required");
		}

		Submission submission = submissionRepository.findOrCreate(sessionId, applicationReference, payload.toString());

		if (submission.status() != SubmissionStatus.RECEIVED) {
			// Already processed by an earlier delivery of this session_id — don't reprocess.
			return new IngestOutcome.Processed(submission.status(), submission.lastError());
		}

		return process(submission, payload);
	}

	private IngestOutcome process(Submission submission, JsonNode payload) {
		ValidationResult validation = validator.validate(payload);
		if (validation instanceof ValidationResult.Invalid invalid) {
			String error = String.join("; ", invalid.errors());
			submissionRepository.updateStatus(submission.id(), SubmissionStatus.SCHEMA_INVALID, error);
			return new IngestOutcome.Processed(SubmissionStatus.SCHEMA_INVALID, error);
		}
		IngestedForm form = ((ValidationResult.Valid) validation).form();

		HttpResponse<GeocodingCoordinates> geocodeResponse = postcodeLookupClient.lookupPostcode(form.address().postcode());
		if (geocodeResponse.statusCode() != 200 || geocodeResponse.body() == null) {
			String error = "postcode lookup failed with status " + geocodeResponse.statusCode();
			submissionRepository.updateStatus(submission.id(), SubmissionStatus.GEOCODE_FAILED, error);
			return new IngestOutcome.Processed(SubmissionStatus.GEOCODE_FAILED, error);
		}

		TransformResult transformResult = transformer.transform(form, geocodeResponse.body());
		if (transformResult instanceof TransformResult.Failure failure) {
			submissionRepository.updateStatus(submission.id(), SubmissionStatus.TRANSFORM_FAILED, failure.reason());
			return new IngestOutcome.Processed(SubmissionStatus.TRANSFORM_FAILED, failure.reason());
		}
		TransformedForm transformed = ((TransformResult.Success) transformResult).form();

		SubmissionStatus finalStatus = persistTransformed(submission.id(), transformed);
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
			outboxEmailRepository.markSent(email.id());
		} else {
			outboxEmailRepository.markFailed(email.id(), "email send failed with status " + response.statusCode());
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	public RetrySummary retryAll() {
		return new RetrySummary(retrySubmissions(), retryOutboxEmails());
	}

	// Re-runs the same process() pipeline against the stored raw_payload — a submission that
	// now succeeds (e.g. after a code fix) goes through persistTransformed()/outbox exactly as
	// it would have on first ingest.
	private int retrySubmissions() {
		List<Submission> needingRetry = submissionRepository.findNeedingRetry();
		for (Submission submission : needingRetry) {
			submissionRepository.incrementRetryCount(submission.id());
			try {
				process(submission, objectMapper.readTree(submission.rawPayload()));
			} catch (JsonProcessingException e) {
				// Can't happen — raw_payload was stored as JSON we produced ourselves.
				throw new IllegalStateException("stored raw_payload is not valid JSON", e);
			}
		}
		return needingRetry.size();
	}

	private int retryOutboxEmails() {
		List<OutboxEmail> undelivered = outboxEmailRepository.findUndelivered();
		for (OutboxEmail email : undelivered) {
			sendNotificationEmail(email.submissionId());
		}
		return undelivered.size();
	}
}
