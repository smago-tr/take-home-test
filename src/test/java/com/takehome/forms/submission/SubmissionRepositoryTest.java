package com.takehome.forms.submission;

import com.takehome.forms.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class SubmissionRepositoryTest extends AbstractIntegrationTest {

	@Autowired
	private SubmissionRepository repository;

	@Test
	void findOrCreateIsIdempotentForSameSessionId() {
		Submission first = repository.findOrCreate("session-1", "APP-1", "{\"a\":1}");
		Submission second = repository.findOrCreate("session-1", "APP-1", "{\"a\":1}");

		assertThat(second.id()).isEqualTo(first.id());
		assertThat(first.status()).isEqualTo(SubmissionStatus.RECEIVED);
		assertThat(first.rawPayload()).contains("\"a\"");
	}

	@Test
	void findByIdReturnsTheMatchingSubmission() {
		Submission created = repository.findOrCreate("session-1", "APP-1", "{}");

		Submission found = repository.findById(created.id());

		assertThat(found.sessionId()).isEqualTo("session-1");
		assertThat(found.applicationReference()).isEqualTo("APP-1");
	}

	@Test
	void findOrCreateCreatesSeparateRowsForDifferentSessionIds() {
		Submission first = repository.findOrCreate("session-1", "APP-1", "{}");
		Submission second = repository.findOrCreate("session-2", "APP-1", "{}");

		assertThat(second.id()).isNotEqualTo(first.id());
	}

	@Test
	void updateStatusPersistsStatusAndError() {
		Submission submission = repository.findOrCreate("session-1", "APP-1", "{}");

		repository.updateStatus(submission.id(), SubmissionStatus.SCHEMA_INVALID, "email is required");
		Submission reloaded = repository.findOrCreate("session-1", "APP-1", "{}");

		assertThat(reloaded.status()).isEqualTo(SubmissionStatus.SCHEMA_INVALID);
		assertThat(reloaded.lastError()).isEqualTo("email is required");
	}

	@Test
	void incrementRetryCountIncrementsFromZero() {
		Submission submission = repository.findOrCreate("session-1", "APP-1", "{}");

		repository.incrementRetryCount(submission.id());
		repository.incrementRetryCount(submission.id());
		Submission reloaded = repository.findOrCreate("session-1", "APP-1", "{}");

		assertThat(reloaded.retryCount()).isEqualTo(2);
	}

	@ParameterizedTest
	@EnumSource(value = SubmissionStatus.class, names = {"READY", "DUPLICATE_APPLICATION"}, mode = EnumSource.Mode.EXCLUDE)
	void findNeedingRetryIncludesNonTerminalStatuses(SubmissionStatus status) {
		Submission submission = repository.findOrCreate("session-1", "APP-1", "{}");
		repository.updateStatus(submission.id(), status, "some error");

		assertThat(repository.findNeedingRetry()).extracting(Submission::id).contains(submission.id());
	}

	@ParameterizedTest
	@EnumSource(value = SubmissionStatus.class, names = {"READY", "DUPLICATE_APPLICATION"})
	void findNeedingRetryExcludesTerminalStatuses(SubmissionStatus status) {
		Submission submission = repository.findOrCreate("session-1", "APP-1", "{}");
		repository.updateStatus(submission.id(), status, null);

		assertThat(repository.findNeedingRetry()).extracting(Submission::id).doesNotContain(submission.id());
	}
}
