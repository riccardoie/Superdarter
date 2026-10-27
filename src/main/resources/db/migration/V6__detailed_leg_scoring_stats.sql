-- There is no historic dart log, so played legs cannot be backfilled accurately.
ALTER TABLE leg_player_stats
    ADD COLUMN scoring_stats_available BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN darts_thrown INTEGER NOT NULL DEFAULT 0 CHECK (darts_thrown >= 0),
    ADD COLUMN first_nine_points INTEGER NOT NULL DEFAULT 0 CHECK (first_nine_points >= 0),
    ADD COLUMN first_nine_darts INTEGER NOT NULL DEFAULT 0
        CHECK (first_nine_darts BETWEEN 0 AND 9),
    ADD COLUMN score40 INTEGER NOT NULL DEFAULT 0 CHECK (score40 >= 0),
    ADD COLUMN score60 INTEGER NOT NULL DEFAULT 0 CHECK (score60 >= 0),
    ADD COLUMN score80 INTEGER NOT NULL DEFAULT 0 CHECK (score80 >= 0),
    ADD COLUMN score100 INTEGER NOT NULL DEFAULT 0 CHECK (score100 >= 0),
    ADD COLUMN score120 INTEGER NOT NULL DEFAULT 0 CHECK (score120 >= 0),
    ADD COLUMN score140 INTEGER NOT NULL DEFAULT 0 CHECK (score140 >= 0),
    ADD COLUMN score160 INTEGER NOT NULL DEFAULT 0 CHECK (score160 >= 0),
    ADD COLUMN score180 INTEGER NOT NULL DEFAULT 0 CHECK (score180 >= 0);

UPDATE leg_player_stats SET scoring_stats_available = TRUE WHERE turn = 0;
ALTER TABLE leg_player_stats ALTER COLUMN scoring_stats_available SET DEFAULT TRUE;
