package com.takehome.forms.transform;

import java.time.LocalDate;

public record TransformedForm(
		String sessionId,
		String applicationReference,
		String firstName,
		String lastName,
		String email,
		String gender,
		LocalDate dateOfBirth,
		String phoneNumber,
		String mobileNumber,
		String addressLine1,
		String addressLine2,
		String addressLine3,
		String postcode,
		String country,
		double longitude,
		double latitude
) {
}
