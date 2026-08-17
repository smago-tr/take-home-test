package com.takehome.forms;

import java.util.List;

public record RetrySummary(List<RetryItemResult> submissions, int emailsRetried) {

	public long submissionsSucceeded() {
		return submissions.stream().filter(RetryItemResult::succeeded).count();
	}

	public long submissionsFailed() {
		return submissions.size() - submissionsSucceeded();
	}
}
