package com.takehome.forms.submission;

import com.takehome.forms.transform.TransformedForm;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TransformedFormRepository {

	private final JdbcTemplate jdbcTemplate;

	public TransformedFormRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	// Bare ON CONFLICT DO NOTHING (no target column) suppresses a conflict on EITHER unique
	// constraint — application_reference (another submission already covers this application)
	// or submission_id (this exact submission was already transformed, e.g. two /retry sweeps
	// racing on it). Either way, false means "don't proceed as if newly transformed"; the narrow
	// submission_id race would mislabel the result DUPLICATE_APPLICATION rather than something
	// more precise, but not crashing is the important guarantee here — /retry isn't expected to
	// run concurrently, so this is an accepted, low-probability edge case, not a correctness bug.
	public boolean insertUnlessApplicationAlreadyTransformed(long submissionId, TransformedForm form) {
		List<Long> inserted = jdbcTemplate.query(
				"INSERT INTO transformed_forms (submission_id, session_id, application_reference, first_name, " +
						"last_name, email, gender, date_of_birth, phone_number, mobile_number, address_line_1, " +
						"address_line_2, address_line_3, postcode, country, longitude, latitude) " +
						"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
						"ON CONFLICT DO NOTHING " +
						"RETURNING id",
				(rs, rowNum) -> rs.getLong("id"),
				submissionId,
				form.sessionId(),
				form.applicationReference(),
				form.firstName(),
				form.lastName(),
				form.email(),
				form.gender(),
				form.dateOfBirth(),
				form.phoneNumber(),
				form.mobileNumber(),
				form.addressLine1(),
				form.addressLine2(),
				form.addressLine3(),
				form.postcode(),
				form.country(),
				form.longitude(),
				form.latitude()
		);
		return !inserted.isEmpty();
	}
}
