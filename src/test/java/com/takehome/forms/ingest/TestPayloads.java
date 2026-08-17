package com.takehome.forms.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class TestPayloads {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private TestPayloads() {
	}

	public static ObjectNode valid() {
		ObjectNode payload = MAPPER.createObjectNode();
		payload.put("session_id", "session-1");
		payload.put("application_reference", "GRU-000001-2026");
		payload.put("name", "Jane Doe");
		payload.put("email", "jane.doe@example.com");
		payload.put("gender", "female");
		payload.put("date_of_birth", "1990-01-01");
		payload.put("phone_number", "07000000000");
		payload.put("mobile_number", "07123456789");

		ObjectNode address = MAPPER.createObjectNode();
		address.put("address_line_1", "1 Test Street");
		address.put("address_line_2", "Testville");
		address.put("address_line_3", "Test County");
		address.put("postcode", "AB1 2CD");
		address.put("country", "United Kingdom");
		payload.set("address", address);

		return payload;
	}

	/** A copy of {@link #valid()} with {@code field} removed. Use "address.x" for nested fields. */
	public static ObjectNode without(String field) {
		ObjectNode payload = valid();
		if (field.startsWith("address.")) {
			((ObjectNode) payload.get("address")).remove(field.substring("address.".length()));
		} else {
			payload.remove(field);
		}
		return payload;
	}

	/** A copy of {@link #valid()} with {@code field} set to {@code value}. Use "address.x" for nested fields. */
	public static ObjectNode with(String field, String value) {
		ObjectNode payload = valid();
		if (field.startsWith("address.")) {
			((ObjectNode) payload.get("address")).put(field.substring("address.".length()), value);
		} else {
			payload.put(field, value);
		}
		return payload;
	}
}
