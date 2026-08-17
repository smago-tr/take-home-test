package com.takehome.forms.providers;

public interface PostcodeLookupClient {

	// Mirrors an unreliable external geocoding API — callers must handle a non-200 statusCode.
	HttpResponse<GeocodingCoordinates> lookupPostcode(String postcode);
}
