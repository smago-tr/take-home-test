package com.takehome.forms.submission;

import java.time.OffsetDateTime;

public record OutboxEmail(
		long id,
		long submissionId,
		EmailStatus status,
		int attempts,
		String lastError,
		OffsetDateTime createdAt,
		OffsetDateTime sentAt
) {
}
