-- application_reference was missing from V1 despite being part of the given transformed schema.
ALTER TABLE transformed_forms ADD COLUMN application_reference TEXT NOT NULL;

-- Guards against the same customer's application being handed to the FORM-BOT twice under two
-- different session_ids (session_id only dedups redelivery of the *same* submission event).
ALTER TABLE transformed_forms ADD CONSTRAINT transformed_forms_application_reference_key UNIQUE (application_reference);

-- DUPLICATE_APPLICATION: a submission whose application_reference was already transformed under
-- a different session_id. It's a valid terminal outcome, not a failure — it just never gets its
-- own transformed_forms row, since another submission already covers that application.
ALTER TABLE submissions DROP CONSTRAINT submissions_status_check;
ALTER TABLE submissions ADD CONSTRAINT submissions_status_check
    CHECK (status IN ('RECEIVED', 'SCHEMA_INVALID', 'GEOCODE_FAILED', 'TRANSFORM_FAILED', 'READY', 'DUPLICATE_APPLICATION'));

-- DUPLICATE_APPLICATION is terminal like READY — neither should ever be picked up by /retry.
DROP INDEX idx_submissions_status_pending;
CREATE INDEX idx_submissions_status_pending ON submissions (status)
    WHERE status NOT IN ('READY', 'DUPLICATE_APPLICATION');
