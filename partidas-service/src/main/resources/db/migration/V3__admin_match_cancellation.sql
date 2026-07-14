ALTER TABLE match
    ALTER COLUMN start_at TYPE TIMESTAMPTZ USING start_at AT TIME ZONE 'UTC',
    ALTER COLUMN end_at TYPE TIMESTAMPTZ USING end_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ADD COLUMN canceled_at TIMESTAMPTZ,
    ADD COLUMN canceled_by UUID,
    ADD COLUMN cancel_reason VARCHAR(500),
    ADD COLUMN cancel_idempotency_key VARCHAR(100);

CREATE UNIQUE INDEX uq_match_cancel_idempotency_key
    ON match (cancel_idempotency_key)
    WHERE cancel_idempotency_key IS NOT NULL;

ALTER TABLE match_event
    ALTER COLUMN occurred_at TYPE TIMESTAMPTZ USING occurred_at AT TIME ZONE 'UTC',
    ADD COLUMN actor_id UUID,
    ADD COLUMN reason VARCHAR(500);

CREATE UNIQUE INDEX uq_team_name_case_insensitive ON team (lower(name));
