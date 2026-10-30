-- Record actual board hits per submitted round. Old rounds cannot be reconstructed.
-- Round-owned rows make undo and game deletion remove their hit counts atomically.
CREATE TABLE round_board_hits (
    round_id BIGINT NOT NULL REFERENCES played_rounds(round_id) ON DELETE CASCADE,
    board_number INTEGER NOT NULL,
    single_hits INTEGER NOT NULL DEFAULT 0,
    double_hits INTEGER NOT NULL DEFAULT 0,
    triple_hits INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (round_id, board_number),
    CONSTRAINT round_board_hits_number CHECK (board_number BETWEEN 1 AND 20 OR board_number = 25),
    CONSTRAINT round_board_hits_count CHECK (
        single_hits >= 0 AND double_hits >= 0 AND triple_hits >= 0
        AND single_hits + double_hits + triple_hits BETWEEN 1 AND 3
    ),
    CONSTRAINT round_board_hits_bull CHECK (board_number <> 25 OR triple_hits = 0)
);

-- Supports aggregating a user's recorded rounds across all retained games.
CREATE INDEX played_rounds_stat_idx ON played_rounds (stat_id);
