-- Only newly submitted rounds have an exact pre-round snapshot.
CREATE TABLE played_rounds (
    round_id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL REFERENCES matches(match_id) ON DELETE CASCADE,
    stat_id BIGINT NOT NULL REFERENCES leg_player_stats(stat_id) ON DELETE CASCADE,
    points INTEGER NOT NULL,
    turn INTEGER NOT NULL,
    triple20s INTEGER NOT NULL,
    bullseyes INTEGER NOT NULL,
    highest_score INTEGER NOT NULL,
    highest_checkout INTEGER NOT NULL,
    scoring_stats_available BOOLEAN NOT NULL,
    darts_thrown INTEGER NOT NULL,
    first_nine_points INTEGER NOT NULL,
    first_nine_darts INTEGER NOT NULL,
    score40 INTEGER NOT NULL,
    score60 INTEGER NOT NULL,
    score80 INTEGER NOT NULL,
    score100 INTEGER NOT NULL,
    score120 INTEGER NOT NULL,
    score140 INTEGER NOT NULL,
    score160 INTEGER NOT NULL,
    score180 INTEGER NOT NULL
);
CREATE INDEX played_rounds_match_idx ON played_rounds(match_id, round_id);
