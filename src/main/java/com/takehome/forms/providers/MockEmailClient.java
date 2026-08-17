package com.takehome.forms.providers;

import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock implementation of an external email provider (e.g. SendGrid).
 * Simulates network latency and a ~5% failure rate, matching the provided
 * TypeScript mock this was ported from.
 */
@Component
public class MockEmailClient implements EmailClient {

	@Override
	public HttpResponse<Void> sendEmail(EmailRequest request) {
		simulateNetworkDelay();

		boolean success = ThreadLocalRandom.current().nextDouble() < 0.95;
		return new HttpResponse<>(success ? 200 : 500, null);
	}

	private void simulateNetworkDelay() {
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
