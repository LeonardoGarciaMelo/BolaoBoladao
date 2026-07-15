ALTER TABLE match
    ADD COLUMN duration_minutes INTEGER,
    ADD COLUMN expected_end_at TIMESTAMPTZ,
    ADD COLUMN started_at TIMESTAMPTZ;

UPDATE match
SET duration_minutes = 105,
    expected_end_at = start_at + INTERVAL '105 minutes';

UPDATE match m
SET started_at = COALESCE(
    (SELECT MIN(e.occurred_at) FROM match_event e
      WHERE e.match_id = m.id AND e.event_type = 'MATCH_STARTED'),
    m.start_at
)
WHERE m.status IN ('IN_PROGRESS', 'FINISHED');

ALTER TABLE match
    ALTER COLUMN duration_minutes SET NOT NULL,
    ALTER COLUMN expected_end_at SET NOT NULL,
    ADD CONSTRAINT chk_match_duration CHECK (duration_minutes BETWEEN 1 AND 300);

CREATE INDEX idx_match_status_start_at ON match (status, start_at);
CREATE INDEX idx_match_status_expected_end_at ON match (status, expected_end_at);

ALTER TABLE match_event
    ADD COLUMN command_key VARCHAR(150),
    ADD COLUMN command_fingerprint VARCHAR(64);

ALTER TABLE match ALTER COLUMN cancel_idempotency_key TYPE VARCHAR(150);

CREATE UNIQUE INDEX uq_match_event_command_key
    ON match_event (command_key)
    WHERE command_key IS NOT NULL;
