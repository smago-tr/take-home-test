package com.takehome.forms.providers;

public interface EmailClient {

	/**
	 * Sends an email. Mirrors an unreliable external provider (e.g. SendGrid) —
	 * callers must handle a non-200 statusCode as a retryable failure.
	 */
	HttpResponse<Void> sendEmail(EmailRequest request);
}
