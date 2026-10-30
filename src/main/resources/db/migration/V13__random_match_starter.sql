-- Existing games retain their original starter and leg rotation.
ALTER TABLE matches ADD COLUMN starting_player_offset INTEGER NOT NULL DEFAULT 0;
ALTER TABLE matches ADD CONSTRAINT matches_starting_player_offset
    CHECK (starting_player_offset BETWEEN 0 AND 9);
