package com.takehome.forms.ingest;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class IngestedFormValidatorTest {

	private final IngestedFormValidator validator = new IngestedFormValidator();

	@Test
	void validPayloadIsValid() {
		assertThat(validator.validate(TestPayloads.valid())).isInstanceOf(ValidationResult.Valid.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"session_id", "application_reference", "name", "email", "gender", "date_of_birth", "mobile_number",
			"address.address_line_1", "address.address_line_2", "address.postcode", "address.country"
	})
	void missingRequiredFieldIsInvalid(String field) {
		assertThat(validator.validate(TestPayloads.without(field))).isInstanceOf(ValidationResult.Invalid.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {"phone_number", "address.address_line_3"})
	void missingOptionalFieldIsStillValid(String field) {
		assertThat(validator.validate(TestPayloads.without(field))).isInstanceOf(ValidationResult.Valid.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {"male", "female", "other"})
	void knownGenderValuesAreValid(String gender) {
		assertThat(validator.validate(TestPayloads.with("gender", gender))).isInstanceOf(ValidationResult.Valid.class);
	}

	@Test
	void unknownGenderValueIsInvalid() {
		assertThat(validator.validate(TestPayloads.with("gender", "nonbinary"))).isInstanceOf(ValidationResult.Invalid.class);
	}

	@Test
	void blankFieldIsTreatedAsMissing() {
		assertThat(validator.validate(TestPayloads.with("email", "   "))).isInstanceOf(ValidationResult.Invalid.class);
	}

	@Test
	void collectsAllErrorsNotJustTheFirst() {
		ObjectNode payload = TestPayloads.without("email");
		payload.remove("name");

		ValidationResult result = validator.validate(payload);

		assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
		assertThat(((ValidationResult.Invalid) result).errors()).hasSizeGreaterThanOrEqualTo(2);
	}
}
