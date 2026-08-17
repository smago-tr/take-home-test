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

	/**
	 * Inserts the transformed form for this submission, unless another submission's
	 * application_reference already has one — in which case nothing is written and this
	 * returns false, so the caller can mark this submission DUPLICATE_APPLICATION instead
	 * of READY rather than handing the FORM-BOT the same application twice.
	 */
	public boolean insertUnlessApplicationAlreadyTransformed(long submissionId, TransformedForm form) {
		List<Long> inserted = jdbcTemplate.query(
				"INSERT INTO transformed_forms (submission_id, session_id, application_reference, first_name, " +
						"last_name, email, gender, date_of_birth, phone_number, mobile_number, address_line_1, " +
						"address_line_2, address_line_3, postcode, country, longitude, latitude) " +
						"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
						"ON CONFLICT (application_reference) DO NOTHING " +
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
