package com.takehome.forms.providers;

import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

// Mock geocoding provider — ~5% failure rate and simulated latency, ported from the given TS mock.
@Component
public class MockPostcodeLookupClient implements PostcodeLookupClient {

	@Override
	public HttpResponse<GeocodingCoordinates> lookupPostcode(String postcode) {
		simulateNetworkDelay();

		boolean success = ThreadLocalRandom.current().nextDouble() < 0.95;
		if (!success) {
			return new HttpResponse<>(500, null);
		}
		return new HttpResponse<>(200, new GeocodingCoordinates(50.05, -5.05));
	}

	private void simulateNetworkDelay() {
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
