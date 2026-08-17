package com.takehome.forms.submission;

import java.time.OffsetDateTime;

public final class TestSubmissions {

	private TestSubmissions() {
	}

	public static Submission withStatus(long id, String sessionId, String applicationReference, SubmissionStatus status) {
		OffsetDateTime now = OffsetDateTime.now();
		return new Submission(id, sessionId, applicationReference, "{}", status, null, 0, now, now);
	}

	public static Submission received(long id, String sessionId, String applicationReference) {
		return withStatus(id, sessionId, applicationReference, SubmissionStatus.RECEIVED);
	}
}
