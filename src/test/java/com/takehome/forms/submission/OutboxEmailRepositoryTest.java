package com.takehome.forms.submission;

import com.takehome.forms.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEmailRepositoryTest extends AbstractIntegrationTest {

	@Autowired
	private SubmissionRepository submissionRepository;

	@Autowired
	private OutboxEmailRepository outboxEmailRepository;

	@Test
	void insertPendingCreatesPendingRow() {
		Submission submission = submissionRepository.findOrCreate("session-1", "APP-1", "{}");

		outboxEmailRepository.insertPending(submission.id());

		OutboxEmail email = outboxEmailRepository.findBySubmissionId(submission.id()).orElseThrow();
		assertThat(email.status()).isEqualTo(EmailStatus.PENDING);
		assertThat(email.attempts()).isZero();
	}

	@Test
	void insertPendingIsIdempotent() {
		Submission submission = submissionRepository.findOrCreate("session-1", "APP-1", "{}");

		outboxEmailRepository.insertPending(submission.id());
		outboxEmailRepository.insertPending(submission.id());

		// UNIQUE on submission_id — a second call must not create a second row.
		assertThat(outboxEmailRepository.findUndelivered()).hasSize(1);
	}

	@Test
	void markSentUpdatesStatus() {
		Submission submission = submissionRepository.findOrCreate("session-1", "APP-1", "{}");
		outboxEmailRepository.insertPending(submission.id());
		OutboxEmail email = outboxEmailRepository.findBySubmissionId(submission.id()).orElseThrow();

		outboxEmailRepository.markSent(email.id());

		OutboxEmail reloaded = outboxEmailRepository.findBySubmissionId(submission.id()).orElseThrow();
		assertThat(reloaded.status()).isEqualTo(EmailStatus.SENT);
		assertThat(reloaded.sentAt()).isNotNull();
	}

	@Test
	void markSentClearsAStaleErrorFromAnEarlierFailedAttempt() {
		Submission submission = submissionRepository.findOrCreate("session-1", "APP-1", "{}");
		outboxEmailRepository.insertPending(submission.id());
		OutboxEmail email = outboxEmailRepository.findBySubmissionId(submission.id()).orElseThrow();
		outboxEmailRepository.markFailed(email.id(), "smtp timeout");

		outboxEmailRepository.markSent(email.id());

		OutboxEmail reloaded = outboxEmailRepository.findBySubmissionId(submission.id()).orElseThrow();
		assertThat(reloaded.status()).isEqualTo(EmailStatus.SENT);
		assertThat(reloaded.lastError()).isNull();
	}

	@Test
	void markFailedIncrementsAttemptsAndSetsError() {
		Submission submission = submissionRepository.findOrCreate("session-1", "APP-1", "{}");
		outboxEmailRepository.insertPending(submission.id());
		OutboxEmail email = outboxEmailRepository.findBySubmissionId(submission.id()).orElseThrow();

		outboxEmailRepository.markFailed(email.id(), "smtp timeout");

		OutboxEmail reloaded = outboxEmailRepository.findBySubmissionId(submission.id()).orElseThrow();
		assertThat(reloaded.status()).isEqualTo(EmailStatus.FAILED);
		assertThat(reloaded.attempts()).isEqualTo(1);
		assertThat(reloaded.lastError()).isEqualTo("smtp timeout");
	}

	@Test
	void findUndeliveredExcludesSent() {
		Submission submission = submissionRepository.findOrCreate("session-1", "APP-1", "{}");
		outboxEmailRepository.insertPending(submission.id());
		OutboxEmail email = outboxEmailRepository.findBySubmissionId(submission.id()).orElseThrow();

		outboxEmailRepository.markSent(email.id());

		assertThat(outboxEmailRepository.findUndelivered()).isEmpty();
	}

	@Test
	void findUndeliveredIncludesFailed() {
		Submission submission = submissionRepository.findOrCreate("session-1", "APP-1", "{}");
		outboxEmailRepository.insertPending(submission.id());
		OutboxEmail email = outboxEmailRepository.findBySubmissionId(submission.id()).orElseThrow();

		outboxEmailRepository.markFailed(email.id(), "smtp timeout");

		assertThat(outboxEmailRepository.findUndelivered()).extracting(OutboxEmail::id).contains(email.id());
	}
}
