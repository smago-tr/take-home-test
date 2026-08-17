-- submissions: raw intake, one row per /ingest call, keyed by session_id.
CREATE TABLE submissions (
    id                     BIGSERIAL PRIMARY KEY,
    session_id             TEXT UNIQUE NOT NULL,
    application_reference  TEXT NOT NULL,
    raw_payload             JSONB NOT NULL,
    status                 TEXT NOT NULL DEFAULT 'RECEIVED'
        CHECK (status IN ('RECEIVED', 'SCHEMA_INVALID', 'GEOCODE_FAILED', 'TRANSFORM_FAILED', 'READY')),
    last_error             TEXT,
    retry_count            INTEGER NOT NULL DEFAULT 0,
    received_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Partial index: only rows that still need attention are candidates for /retry.
CREATE INDEX idx_submissions_status_pending ON submissions (status) WHERE status <> 'READY';

-- transformed_forms: one row per successfully transformed submission.
CREATE TABLE transformed_forms (
    id             BIGSERIAL PRIMARY KEY,
    submission_id  BIGINT UNIQUE NOT NULL REFERENCES submissions (id),
    session_id     TEXT NOT NULL,
    first_name     TEXT NOT NULL,
    last_name      TEXT NOT NULL,
    email          TEXT NOT NULL,
    gender         TEXT NOT NULL CHECK (gender IN ('male', 'female', 'prefer-not-to-say')),
    date_of_birth  DATE NOT NULL,
    phone_number   TEXT,
    mobile_number  TEXT NOT NULL,
    address_line_1 TEXT NOT NULL,
    address_line_2 TEXT NOT NULL,
    address_line_3 TEXT,
    postcode       TEXT NOT NULL,
    country        TEXT NOT NULL,
    longitude      DOUBLE PRECISION NOT NULL,
    latitude       DOUBLE PRECISION NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- outbox_emails: guaranteed, at-most-once email delivery per submission.
CREATE TABLE outbox_emails (
    id             BIGSERIAL PRIMARY KEY,
    submission_id  BIGINT UNIQUE NOT NULL REFERENCES submissions (id),
    status         TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts       INTEGER NOT NULL DEFAULT 0,
    last_error     TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at        TIMESTAMPTZ
);

-- Partial index: only PENDING rows are candidates for (re)send.
CREATE INDEX idx_outbox_emails_pending ON outbox_emails (status) WHERE status = 'PENDING';
