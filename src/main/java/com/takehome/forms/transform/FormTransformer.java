package com.takehome.forms.transform;

import com.takehome.forms.ingest.IngestedForm;
import com.takehome.forms.providers.GeocodingCoordinates;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Component
public class FormTransformer {

	public TransformResult transform(IngestedForm form, GeocodingCoordinates coordinates) {
		LocalDate dateOfBirth;
		try {
			dateOfBirth = LocalDate.parse(form.dateOfBirth());
		} catch (DateTimeParseException e) {
			return new TransformResult.Failure("date_of_birth: unparseable value '" + form.dateOfBirth() + "'");
		}

		String gender = switch (form.gender()) {
			case "male" -> "male";
			case "female" -> "female";
			case "other" -> "prefer-not-to-say";
			default -> throw new IllegalStateException("unexpected gender reached transform: " + form.gender());
		};

		// First-space split: everything before the first space is the first name, everything
		// after is the surname. A single-word name has no surname to capture (lastName = "").
		int spaceIndex = form.name().indexOf(' ');
		String firstName = spaceIndex < 0 ? form.name() : form.name().substring(0, spaceIndex);
		String lastName = spaceIndex < 0 ? "" : form.name().substring(spaceIndex + 1);

		IngestedForm.Address address = form.address();
		TransformedForm transformed = new TransformedForm(
				form.sessionId(),
				form.applicationReference(),
				firstName,
				lastName,
				form.email(),
				gender,
				dateOfBirth,
				form.phoneNumber(),
				form.mobileNumber(),
				address.addressLine1(),
				address.addressLine2(),
				address.addressLine3(),
				address.postcode(),
				address.country(),
				coordinates.longitude(),
				coordinates.latitude()
		);
		return new TransformResult.Success(transformed);
	}
}
