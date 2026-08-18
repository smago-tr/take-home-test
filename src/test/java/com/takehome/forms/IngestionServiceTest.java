package com.takehome.forms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.takehome.forms.ingest.IngestedFormValidator;
import com.takehome.forms.ingest.TestIngestedForms;
import com.takehome.forms.ingest.TestPayloads;
import com.takehome.forms.ingest.ValidationResult;
import com.takehome.forms.providers.EmailClient;
import com.takehome.forms.providers.GeocodingCoordinates;
import com.takehome.forms.providers.HttpResponse;
import com.takehome.forms.providers.PostcodeLookupClient;
import com.takehome.forms.submission.EmailStatus;
import com.takehome.forms.submission.OutboxEmail;
import com.takehome.forms.submission.OutboxEmailRepository;
import com.takehome.forms.submission.Submission;
import com.takehome.forms.submission.SubmissionRepository;
import com.takehome.forms.submission.SubmissionStatus;
import com.takehome.forms.submission.TestSubmissions;
import com.takehome.forms.submission.TransformedFormRepository;
import com.takehome.forms.transform.FormTransformer;
import com.takehome.forms.transform.TestTransformedForms;
import com.takehome.forms.transform.TransformResult;
import com.takehome.forms.transform.TransformedForm;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

	@Mock
	private IngestedFormValidator validator;
	@Mock
	private PostcodeLookupClient postcodeLookupClient;
	@Mock
	private FormTransformer transformer;
	@Mock
	private EmailClient emailClient;
	@Mock
	private SubmissionRepository submissionRepository;
	@Mock
	private TransformedFormRepository transformedFormRepository;
	@Mock
	private OutboxEmailRepository outboxEmailRepository;
	@Mock
	private PlatformTransactionManager transactionManager;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private IngestionService service;

	@BeforeEach
	void setUp() {
		FormsMetrics metrics = new FormsMetrics(new SimpleMeterRegistry(), submissionRepository);
		service = new IngestionService(validator, postcodeLookupClient, transformer, emailClient,
				submissionRepository, transformedFormRepository, outboxEmailRepository, objectMapper, metrics,
				transactionManager);
	}

	@Test
	void missingSessionIdIsMalformed() {
		JsonNode payload = objectMapper.createObjectNode();

		IngestOutcome outcome = service.ingest(payload);

		assertThat(outcome).isInstanceOf(IngestOutcome.MalformedRequest.class);
		verifyNoInteractions(submissionRepository);
	}

	@Test
	void duplicateDeliveryReturnsExistingOutcomeWithoutReprocessing() {
		Submission existing = TestSubmissions.withStatus(1, "sid", "ref", SubmissionStatus.READY);
		when(submissionRepository.findOrCreate(eq("sid"), eq("ref"), anyString())).thenReturn(existing);

		ObjectNode payload = TestPayloads.valid();
		payload.put("session_id", "sid");
		payload.put("application_reference", "ref");

		IngestOutcome outcome = service.ingest(payload);

		assertThat(outcome).isEqualTo(new IngestOutcome.Processed(SubmissionStatus.READY, null));
		verifyNoInteractions(validator);
	}

	@Test
	void schemaInvalidUpdatesStatusAndReturnsErrors() {
		Submission fresh = TestSubmissions.received(1, "sid", "ref");
		when(submissionRepository.findOrCreate(anyString(), anyString(), anyString())).thenReturn(fresh);
		when(validator.validate(any())).thenReturn(new ValidationResult.Invalid(List.of("email is required")));

		IngestOutcome outcome = service.ingest(TestPayloads.valid());

		assertThat(outcome).isEqualTo(new IngestOutcome.Processed(SubmissionStatus.SCHEMA_INVALID, "email is required"));
		verify(submissionRepository).updateStatus(1L, SubmissionStatus.SCHEMA_INVALID, "email is required");
		verifyNoInteractions(postcodeLookupClient);
	}

	@Test
	void geocodeFailureUpdatesStatus() {
		Submission fresh = TestSubmissions.received(1, "sid", "ref");
		when(submissionRepository.findOrCreate(anyString(), anyString(), anyString())).thenReturn(fresh);
		when(validator.validate(any())).thenReturn(new ValidationResult.Valid(TestIngestedForms.valid()));
		when(postcodeLookupClient.lookupPostcode(anyString())).thenReturn(new HttpResponse<>(500, null));

		IngestOutcome outcome = service.ingest(TestPayloads.valid());

		assertThat(((IngestOutcome.Processed) outcome).status()).isEqualTo(SubmissionStatus.GEOCODE_FAILED);
		verify(submissionRepository).updateStatus(eq(1L), eq(SubmissionStatus.GEOCODE_FAILED), anyString());
		verifyNoInteractions(transformer);
	}

	@Test
	void transformFailureUpdatesStatus() {
		Submission fresh = TestSubmissions.received(1, "sid", "ref");
		when(submissionRepository.findOrCreate(anyString(), anyString(), anyString())).thenReturn(fresh);
		when(validator.validate(any())).thenReturn(new ValidationResult.Valid(TestIngestedForms.valid()));
		when(postcodeLookupClient.lookupPostcode(anyString()))
				.thenReturn(new HttpResponse<>(200, new GeocodingCoordinates(1, 2)));
		when(transformer.transform(any(), any())).thenReturn(new TransformResult.Failure("date_of_birth: unparseable"));

		IngestOutcome outcome = service.ingest(TestPayloads.valid());

		assertThat(((IngestOutcome.Processed) outcome).status()).isEqualTo(SubmissionStatus.TRANSFORM_FAILED);
		verify(transformedFormRepository, never()).insertUnlessApplicationAlreadyTransformed(any(Long.class), any());
	}

	@Test
	void successfulPipelinePersistsAndSendsEmail() {
		Submission fresh = TestSubmissions.received(1, "sid", "ref");
		TransformedForm transformed = TestTransformedForms.withSessionAndApplication("sid", "ref");
		when(submissionRepository.findOrCreate(anyString(), anyString(), anyString())).thenReturn(fresh);
		when(validator.validate(any())).thenReturn(new ValidationResult.Valid(TestIngestedForms.valid()));
		when(postcodeLookupClient.lookupPostcode(anyString()))
				.thenReturn(new HttpResponse<>(200, new GeocodingCoordinates(1, 2)));
		when(transformer.transform(any(), any())).thenReturn(new TransformResult.Success(transformed));
		when(transformedFormRepository.insertUnlessApplicationAlreadyTransformed(1L, transformed)).thenReturn(true);
		when(outboxEmailRepository.findBySubmissionId(1L)).thenReturn(
				Optional.of(new OutboxEmail(10, 1, EmailStatus.PENDING, 0, null, OffsetDateTime.now(), null)));
		when(emailClient.sendEmail(any())).thenReturn(new HttpResponse<>(200, null));

		IngestOutcome outcome = service.ingest(TestPayloads.valid());

		assertThat(((IngestOutcome.Processed) outcome).status()).isEqualTo(SubmissionStatus.READY);
		verify(submissionRepository).updateStatus(1L, SubmissionStatus.READY, null);
		verify(outboxEmailRepository).insertPending(1L);
		verify(outboxEmailRepository).markSent(10L);
	}

	@Test
	void duplicateApplicationDoesNotOverwriteExistingTransform() {
		Submission fresh = TestSubmissions.received(1, "sid", "ref");
		TransformedForm transformed = TestTransformedForms.withSessionAndApplication("sid", "ref");
		when(submissionRepository.findOrCreate(anyString(), anyString(), anyString())).thenReturn(fresh);
		when(validator.validate(any())).thenReturn(new ValidationResult.Valid(TestIngestedForms.valid()));
		when(postcodeLookupClient.lookupPostcode(anyString()))
				.thenReturn(new HttpResponse<>(200, new GeocodingCoordinates(1, 2)));
		when(transformer.transform(any(), any())).thenReturn(new TransformResult.Success(transformed));
		when(transformedFormRepository.insertUnlessApplicationAlreadyTransformed(1L, transformed)).thenReturn(false);
		when(outboxEmailRepository.findBySubmissionId(1L)).thenReturn(
				Optional.of(new OutboxEmail(10, 1, EmailStatus.PENDING, 0, null, OffsetDateTime.now(), null)));
		when(emailClient.sendEmail(any())).thenReturn(new HttpResponse<>(200, null));

		IngestOutcome outcome = service.ingest(TestPayloads.valid());

		assertThat(((IngestOutcome.Processed) outcome).status()).isEqualTo(SubmissionStatus.DUPLICATE_APPLICATION);
		verify(submissionRepository).updateStatus(1L, SubmissionStatus.DUPLICATE_APPLICATION, null);
	}

	@Test
	void oneSubmissionFailingDuringRetryDoesNotAbortTheRest() {
		Submission first = TestSubmissions.withStatus(1, "s1", "r1", SubmissionStatus.GEOCODE_FAILED);
		Submission second = TestSubmissions.withStatus(2, "s2", "r2", SubmissionStatus.GEOCODE_FAILED);
		when(submissionRepository.findNeedingRetry()).thenReturn(List.of(first, second));
		when(outboxEmailRepository.findUndelivered()).thenReturn(List.of());
		// First call (for `first`) blows up unexpectedly; second call (for `second`) behaves normally.
		when(validator.validate(any()))
				.thenThrow(new RuntimeException("boom"))
				.thenReturn(new ValidationResult.Invalid(List.of("still broken")));

		RetrySummary summary = service.retryAll();

		assertThat(summary.submissions()).hasSize(2);
		assertThat(summary.submissionsSucceeded()).isZero();
		assertThat(summary.submissionsFailed()).isEqualTo(2);
		assertThat(summary.submissions()).extracting(RetryItemResult::submissionId, RetryItemResult::status)
				.containsExactlyInAnyOrder(tuple(1L, "ERROR"), tuple(2L, "SCHEMA_INVALID"));
		verify(submissionRepository).incrementRetryCount(1L);
		verify(submissionRepository).incrementRetryCount(2L);
		verify(submissionRepository).updateStatus(2L, SubmissionStatus.SCHEMA_INVALID, "still broken");
		verify(submissionRepository, never()).updateStatus(eq(1L), any(), anyString());
	}

	@Test
	void successfulRetryReportsSucceededWithApplicationReference() {
		Submission submission = TestSubmissions.withStatus(1, "sid", "APP-1", SubmissionStatus.GEOCODE_FAILED);
		TransformedForm transformed = TestTransformedForms.withSessionAndApplication("sid", "APP-1");
		when(submissionRepository.findNeedingRetry()).thenReturn(List.of(submission));
		when(outboxEmailRepository.findUndelivered()).thenReturn(List.of());
		when(validator.validate(any())).thenReturn(new ValidationResult.Valid(TestIngestedForms.valid()));
		when(postcodeLookupClient.lookupPostcode(anyString()))
				.thenReturn(new HttpResponse<>(200, new GeocodingCoordinates(1, 2)));
		when(transformer.transform(any(), any())).thenReturn(new TransformResult.Success(transformed));
		when(transformedFormRepository.insertUnlessApplicationAlreadyTransformed(1L, transformed)).thenReturn(true);
		when(outboxEmailRepository.findBySubmissionId(1L)).thenReturn(
				Optional.of(new OutboxEmail(10, 1, EmailStatus.PENDING, 0, null, OffsetDateTime.now(), null)));
		when(emailClient.sendEmail(any())).thenReturn(new HttpResponse<>(200, null));

		RetrySummary summary = service.retryAll();

		assertThat(summary.submissionsSucceeded()).isEqualTo(1);
		assertThat(summary.submissions()).containsExactly(
				new RetryItemResult(1L, "APP-1", true, "READY", null));
	}

	@Test
	void oneEmailFailingDuringRetryDoesNotAbortTheRest() {
		when(submissionRepository.findNeedingRetry()).thenReturn(List.of());
		OutboxEmail first = new OutboxEmail(10, 1, EmailStatus.FAILED, 1, "prev error", OffsetDateTime.now(), null);
		OutboxEmail second = new OutboxEmail(11, 2, EmailStatus.FAILED, 1, "prev error", OffsetDateTime.now(), null);
		when(outboxEmailRepository.findUndelivered()).thenReturn(List.of(first, second));
		when(outboxEmailRepository.findBySubmissionId(1L)).thenThrow(new RuntimeException("boom"));
		when(outboxEmailRepository.findBySubmissionId(2L)).thenReturn(Optional.of(second));
		when(emailClient.sendEmail(any())).thenReturn(new HttpResponse<>(200, null));

		RetrySummary summary = service.retryAll();

		assertThat(summary.emailsRetried()).isEqualTo(2);
		verify(outboxEmailRepository).markSent(11L);
	}
}
