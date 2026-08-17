package com.takehome.forms.ingest;

public final class TestIngestedForms {

	private static final IngestedForm.Address ADDRESS =
			new IngestedForm.Address("1 Test Street", "Testville", "Test County", "AB1 2CD", "United Kingdom");

	private TestIngestedForms() {
	}

	public static IngestedForm valid() {
		return withName("Jane Doe");
	}

	public static IngestedForm withName(String name) {
		return new IngestedForm(
				"session-1", "GRU-000001-2026", name, "jane.doe@example.com", "female",
				"1990-01-01", "07000000000", "07123456789", ADDRESS
		);
	}

	public static IngestedForm withGender(String gender) {
		return new IngestedForm(
				"session-1", "GRU-000001-2026", "Jane Doe", "jane.doe@example.com", gender,
				"1990-01-01", "07000000000", "07123456789", ADDRESS
		);
	}

	public static IngestedForm withDateOfBirth(String dateOfBirth) {
		return new IngestedForm(
				"session-1", "GRU-000001-2026", "Jane Doe", "jane.doe@example.com", "female",
				dateOfBirth, "07000000000", "07123456789", ADDRESS
		);
	}
}
