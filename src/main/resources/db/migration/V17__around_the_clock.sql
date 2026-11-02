INSERT INTO gametypes (gametype) VALUES ('Around the Clock')
    ON CONFLICT (gametype) DO NOTHING;

ALTER TABLE legs ADD COLUMN clock_state JSONB;
ALTER TABLE played_rounds ADD COLUMN before_clock_state JSONB;
