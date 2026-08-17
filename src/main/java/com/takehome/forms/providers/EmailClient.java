package com.takehome.forms.providers;

public interface EmailClient {

	// Mirrors an unreliable external provider — callers must handle a non-200 statusCode.
	HttpResponse<Void> sendEmail(EmailRequest request);
}
