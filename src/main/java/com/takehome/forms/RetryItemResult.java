package com.takehome.forms;

public record RetryItemResult(
		long submissionId,
		String applicationReference,
		boolean succeeded,
		String status,
		String error
) {
}
