package com.takehome.forms.transform;

import com.takehome.forms.ingest.IngestedForm;
import com.takehome.forms.ingest.TestIngestedForms;
import com.takehome.forms.providers.GeocodingCoordinates;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class FormTransformerTest {

	private final FormTransformer transformer = new FormTransformer();
	private final GeocodingCoordinates coordinates = new GeocodingCoordinates(50.05, -5.05);

	@ParameterizedTest
	@CsvSource({
			"male, male",
			"female, female",
			"other, prefer-not-to-say"
	})
	void mapsGenderCorrectly(String ingestedGender, String expectedTransformedGender) {
		TransformResult result = transformer.transform(TestIngestedForms.withGender(ingestedGender), coordinates);

		assertThat(result).isInstanceOf(TransformResult.Success.class);
		assertThat(((TransformResult.Success) result).form().gender()).isEqualTo(expectedTransformedGender);
	}

	@ParameterizedTest
	@CsvSource({
			"Cher, Cher, ''",
			"'Jane Doe', Jane, Doe",
			"'Andy James Smith-Jones', Andy, 'James Smith-Jones'"
	})
	void splitsNameOnFirstSpace(String name, String expectedFirstName, String expectedLastName) {
		TransformResult result = transformer.transform(TestIngestedForms.withName(name), coordinates);
		TransformedForm transformed = ((TransformResult.Success) result).form();

		assertThat(transformed.firstName()).isEqualTo(expectedFirstName);
		assertThat(transformed.lastName()).isEqualTo(expectedLastName);
	}

	@Test
	void unparseableDateOfBirthIsFailure() {
		TransformResult result = transformer.transform(TestIngestedForms.withDateOfBirth("not-a-date"), coordinates);

		assertThat(result).isInstanceOf(TransformResult.Failure.class);
	}

	@Test
	void copiesGeocodingCoordinatesThrough() {
		IngestedForm form = TestIngestedForms.valid();

		TransformResult result = transformer.transform(form, new GeocodingCoordinates(1.23, 4.56));
		TransformedForm transformed = ((TransformResult.Success) result).form();

		assertThat(transformed.longitude()).isEqualTo(1.23);
		assertThat(transformed.latitude()).isEqualTo(4.56);
	}
}
