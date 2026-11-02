INSERT INTO gametypes (gametype) VALUES
    ('Mikke Mus Forced'), ('Mikke Mus Relaxed') ON CONFLICT (gametype) DO NOTHING;

-- Leg-owned state includes marks, redemption order and bull-off history.
-- A full pre-command copy makes scoring, bull-offs and decisions undoable.
ALTER TABLE legs ADD COLUMN mikke_state JSONB;
ALTER TABLE played_rounds ADD COLUMN before_mikke_state JSONB;
