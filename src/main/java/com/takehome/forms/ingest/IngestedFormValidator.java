package com.takehome.forms.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class IngestedFormValidator {

	private static final List<String> KNOWN_GENDERS = List.of("male", "female", "other");

	public ValidationResult validate(JsonNode payload) {
		List<String> errors = new ArrayList<>();

		String sessionId = requireText(payload, "session_id", errors, "session_id");
		String applicationReference = requireText(payload, "application_reference", errors, "application_reference");
		String name = requireText(payload, "name", errors, "name");
		String email = requireText(payload, "email", errors, "email");
		String gender = requireText(payload, "gender", errors, "gender");
		String dateOfBirth = requireText(payload, "date_of_birth", errors, "date_of_birth");
		String mobileNumber = requireText(payload, "mobile_number", errors, "mobile_number");
		String phoneNumber = payload.path("phone_number").asText(null);

		if (gender != null && !KNOWN_GENDERS.contains(gender)) {
			errors.add("gender: unrecognised value '" + gender + "'");
		}

		JsonNode addressNode = payload.path("address");
		String addressLine1 = requireText(addressNode, "address_line_1", errors, "address.address_line_1");
		String addressLine2 = requireText(addressNode, "address_line_2", errors, "address.address_line_2");
		String addressLine3 = addressNode.path("address_line_3").asText(null);
		String postcode = requireText(addressNode, "postcode", errors, "address.postcode");
		String country = requireText(addressNode, "country", errors, "address.country");

		if (!errors.isEmpty()) {
			return new ValidationResult.Invalid(errors);
		}

		IngestedForm form = new IngestedForm(
				sessionId, applicationReference, name, email, gender, dateOfBirth,
				phoneNumber, mobileNumber,
				new IngestedForm.Address(addressLine1, addressLine2, addressLine3, postcode, country)
		);
		return new ValidationResult.Valid(form);
	}

	/**
	 * path(field) never returns a real null (MissingNode instead), so the call is safe
	 */
	private static String requireText(JsonNode node, String field, List<String> errors, String label) {
		String value = node.path(field).asText(null);
		if (value == null || value.isBlank()) {
			errors.add(label + " is required");
			return null;
		}
		return value;
	}
}
