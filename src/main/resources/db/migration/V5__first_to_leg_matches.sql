-- Existing single-leg matches retain their original rules and history.
ALTER TABLE matches ADD COLUMN legs_to_win INTEGER NOT NULL DEFAULT 1
    CHECK (legs_to_win BETWEEN 1 AND 99);
