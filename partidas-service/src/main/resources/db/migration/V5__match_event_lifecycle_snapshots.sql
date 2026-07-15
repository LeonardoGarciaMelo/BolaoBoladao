ALTER TABLE match_event
    ADD COLUMN duration_minutes_at_event INTEGER,
    ADD COLUMN expected_end_at_event TIMESTAMPTZ,
    ADD COLUMN started_at_event TIMESTAMPTZ,
    ADD COLUMN ended_at_event TIMESTAMPTZ;

UPDATE match_event event
SET duration_minutes_at_event = match.duration_minutes,
    expected_end_at_event = match.start_at + make_interval(mins => match.duration_minutes),
    started_at_event = (
        SELECT MIN(started.occurred_at)
        FROM match_event started
        WHERE started.match_id = event.match_id
          AND started.event_type = 'MATCH_STARTED'
          AND started.id <= event.id
    ),
    ended_at_event = CASE
        WHEN EXISTS (
            SELECT 1
            FROM match_event ended
            WHERE ended.match_id = event.match_id
              AND ended.event_type = 'MATCH_ENDED'
              AND ended.id <= event.id
        ) THEN match.end_at
        ELSE NULL
    END
FROM match
WHERE match.id = event.match_id;

ALTER TABLE match_event
    ALTER COLUMN duration_minutes_at_event SET NOT NULL,
    ALTER COLUMN expected_end_at_event SET NOT NULL;
