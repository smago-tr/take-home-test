package com.takehome.forms.providers;

public interface PostcodeLookupClient {

	/**
	 * Looks up longitude/latitude for a postcode. Mirrors an unreliable external
	 * geocoding API — callers must handle a non-200 statusCode as a retryable failure.
	 */
	HttpResponse<GeocodingCoordinates> lookupPostcode(String postcode);
}
