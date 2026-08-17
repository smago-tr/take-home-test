package com.takehome.forms.submission;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class SubmissionRepository {

	private static final String SELECT_COLUMNS =
			"id, session_id, application_reference, raw_payload, status, last_error, retry_count, received_at, updated_at";

	private static final RowMapper<Submission> ROW_MAPPER = (rs, rowNum) -> new Submission(
			rs.getLong("id"),
			rs.getString("session_id"),
			rs.getString("application_reference"),
			rs.getString("raw_payload"),
			SubmissionStatus.valueOf(rs.getString("status")),
			rs.getString("last_error"),
			rs.getInt("retry_count"),
			rs.getObject("received_at", OffsetDateTime.class),
			rs.getObject("updated_at", OffsetDateTime.class)
	);

	private final JdbcTemplate jdbcTemplate;

	public SubmissionRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/**
	 * Atomically inserts a new submission, or — if session_id was already seen (the 3rd party
	 * doesn't guarantee exactly-once delivery) — returns the existing row untouched. Uses
	 * INSERT ... ON CONFLICT rather than SELECT-then-INSERT to avoid a race between two
	 * concurrent deliveries of the same session_id.
	 */
	public Submission findOrCreate(String sessionId, String applicationReference, String rawPayloadJson) {
		List<Submission> inserted = jdbcTemplate.query(
				"INSERT INTO submissions (session_id, application_reference, raw_payload) " +
						"VALUES (?, ?, ?::jsonb) " +
						"ON CONFLICT (session_id) DO NOTHING " +
						"RETURNING " + SELECT_COLUMNS,
				ROW_MAPPER,
				sessionId, applicationReference, rawPayloadJson
		);

		if (!inserted.isEmpty()) {
			return inserted.get(0);
		}

		return jdbcTemplate.queryForObject(
				"SELECT " + SELECT_COLUMNS + " FROM submissions WHERE session_id = ?",
				ROW_MAPPER,
				sessionId
		);
	}

	public void updateStatus(long id, SubmissionStatus status, String error) {
		jdbcTemplate.update(
				"UPDATE submissions SET status = ?, last_error = ?, updated_at = now() WHERE id = ?",
				status.name(), error, id
		);
	}

	public void incrementRetryCount(long id) {
		jdbcTemplate.update(
				"UPDATE submissions SET retry_count = retry_count + 1, updated_at = now() WHERE id = ?",
				id
		);
	}
}
