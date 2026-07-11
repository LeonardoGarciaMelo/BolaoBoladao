CREATE TABLE team (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(120) NOT NULL UNIQUE
);

CREATE TABLE match (
    id                  UUID PRIMARY KEY,
    team_home_id        BIGINT NOT NULL REFERENCES team (id),
    team_away_id        BIGINT NOT NULL REFERENCES team (id),
    team_home_score     INTEGER,
    team_away_score     INTEGER,
    start_at            TIMESTAMP NOT NULL,
    end_at              TIMESTAMP,
    status              VARCHAR(20) NOT NULL,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL,
    CONSTRAINT chk_match_teams_different CHECK (team_home_id <> team_away_id)
);

CREATE INDEX idx_match_status ON match (status);
CREATE INDEX idx_match_start_at ON match (start_at);

CREATE TABLE match_event (
    id                          BIGSERIAL PRIMARY KEY,
    match_id                    UUID NOT NULL REFERENCES match (id),
    event_type                  VARCHAR(30) NOT NULL,
    team_home_score_at_event    INTEGER,
    team_away_score_at_event    INTEGER,
    occurred_at                 TIMESTAMP NOT NULL
);

CREATE INDEX idx_match_event_match_id ON match_event (match_id);
