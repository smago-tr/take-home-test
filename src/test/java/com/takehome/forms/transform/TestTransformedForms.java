package com.takehome.forms.transform;

import java.time.LocalDate;

public final class TestTransformedForms {

	private TestTransformedForms() {
	}

	public static TransformedForm withSessionAndApplication(String sessionId, String applicationReference) {
		return new TransformedForm(
				sessionId, applicationReference, "Jane", "Doe", "jane.doe@example.com", "female",
				LocalDate.of(1990, 1, 1), "07000000000", "07123456789",
				"1 Test Street", "Testville", "Test County", "AB1 2CD", "United Kingdom",
				50.05, -5.05
		);
	}
}
