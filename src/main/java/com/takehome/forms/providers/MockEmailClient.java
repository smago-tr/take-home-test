package com.takehome.forms.providers;

import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

// Mock email provider — ~5% failure rate and simulated latency, ported from the given TS mock.
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
