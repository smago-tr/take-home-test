package com.takehome.forms.ingest;

/**
 * A validated ingested form — only ever constructed once every required field
 * has passed.
 */
public record IngestedForm(
		String sessionId,
		String applicationReference,
		String name,
		String email,
		String gender,
		String dateOfBirth,
		String phoneNumber,
		String mobileNumber,
		Address address
) {
	public record Address(
			String addressLine1,
			String addressLine2,
			String addressLine3,
			String postcode,
			String country
	) {
	}
}
