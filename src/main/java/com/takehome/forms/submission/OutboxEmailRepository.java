package com.takehome.forms.submission;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class OutboxEmailRepository {

	private static final String SELECT_COLUMNS =
			"id, submission_id, status, attempts, last_error, created_at, sent_at";

	private static final RowMapper<OutboxEmail> ROW_MAPPER = (rs, rowNum) -> new OutboxEmail(
			rs.getLong("id"),
			rs.getLong("submission_id"),
			EmailStatus.valueOf(rs.getString("status")),
			rs.getInt("attempts"),
			rs.getString("last_error"),
			rs.getObject("created_at", OffsetDateTime.class),
			rs.getObject("sent_at", OffsetDateTime.class)
	);

	private final JdbcTemplate jdbcTemplate;

	public OutboxEmailRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	// Call in the same transaction as the READY/DUPLICATE_APPLICATION status update.
	public void insertPending(long submissionId) {
		jdbcTemplate.update(
				"INSERT INTO outbox_emails (submission_id) VALUES (?) ON CONFLICT (submission_id) DO NOTHING",
				submissionId
		);
	}

	// Clears last_error too — otherwise a row that failed once and later succeeded on retry
	// would show SENT while still carrying the stale error text from the earlier attempt.
	public void markSent(long id) {
		jdbcTemplate.update(
				"UPDATE outbox_emails SET status = 'SENT', sent_at = now(), last_error = NULL WHERE id = ?", id);
	}

	public void markFailed(long id, String error) {
		jdbcTemplate.update(
				"UPDATE outbox_emails SET status = 'FAILED', last_error = ?, attempts = attempts + 1 WHERE id = ?",
				error, id
		);
	}

	public Optional<OutboxEmail> findBySubmissionId(long submissionId) {
		List<OutboxEmail> rows = jdbcTemplate.query(
				"SELECT " + SELECT_COLUMNS + " FROM outbox_emails WHERE submission_id = ?",
				ROW_MAPPER, submissionId
		);
		return rows.stream().findFirst();
	}

	/** Rows still needing delivery — PENDING (never attempted) or FAILED (attempted, didn't send). */
	public List<OutboxEmail> findUndelivered() {
		return jdbcTemplate.query(
				"SELECT " + SELECT_COLUMNS + " FROM outbox_emails WHERE status <> 'SENT'",
				ROW_MAPPER
		);
	}
}
