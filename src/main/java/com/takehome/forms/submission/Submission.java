package com.takehome.forms.submission;

import java.time.OffsetDateTime;

public record Submission(
		long id,
		String sessionId,
		String applicationReference,
		String rawPayload,
		SubmissionStatus status,
		String lastError,
		int retryCount,
		OffsetDateTime receivedAt,
		OffsetDateTime updatedAt
) {
}
