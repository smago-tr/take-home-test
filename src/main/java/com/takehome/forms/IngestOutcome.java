package com.takehome.forms;

import com.takehome.forms.submission.SubmissionStatus;

public sealed interface IngestOutcome {

	/** No session_id/application_reference to even identify or dedupe the delivery by. */
	record MalformedRequest(String reason) implements IngestOutcome {
	}

	record Processed(SubmissionStatus status, String error) implements IngestOutcome {
	}
}
