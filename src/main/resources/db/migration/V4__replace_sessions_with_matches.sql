-- BREAKING RELEASE: use a fresh database. Never discard deployed history automatically.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM sessions) OR EXISTS (SELECT 1 FROM games)
       OR EXISTS (SELECT 1 FROM playerstats WHERE played_games > 0) THEN
        RAISE EXCEPTION 'Match refactor requires a fresh database. See docs/render-match-cutover.md';
    END IF;
END $$;

DROP TABLE gamestats;
DROP TABLE session_mm_users;
DROP TABLE games;
DROP TABLE sessions;

ALTER TABLE playerstats RENAME COLUMN played_games TO played_matches;
ALTER TABLE playerstats RENAME COLUMN won_games TO won_matches;
ALTER TABLE playerstats ADD COLUMN played_legs INTEGER NOT NULL DEFAULT 0;
ALTER TABLE playerstats ADD COLUMN won_legs INTEGER NOT NULL DEFAULT 0;

CREATE TABLE matches (
    match_id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES users(user_id),
    gametype_id BIGINT NOT NULL REFERENCES gametypes(gametype_id),
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING','IN_PROGRESS','COMPLETED','ABANDONED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    winner_id BIGINT REFERENCES users(user_id),
    revision BIGINT NOT NULL DEFAULT 0,
    CHECK ((status = 'COMPLETED') = (winner_id IS NOT NULL))
);
CREATE TABLE match_participants (
    participant_id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL REFERENCES matches(match_id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    position INTEGER NOT NULL CHECK (position >= 0 AND position < 10),
    UNIQUE (match_id, user_id),
    UNIQUE (match_id, position)
);
CREATE INDEX match_participants_user_idx ON match_participants(user_id);
CREATE INDEX matches_created_idx ON matches(created_at DESC);

CREATE TABLE legs (
    leg_id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL REFERENCES matches(match_id) ON DELETE CASCADE,
    leg_number INTEGER NOT NULL CHECK (leg_number > 0),
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING','IN_PROGRESS','COMPLETED','ABANDONED')),
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    winner_id BIGINT REFERENCES users(user_id),
    UNIQUE (match_id, leg_number),
    CHECK ((status = 'COMPLETED') = (winner_id IS NOT NULL))
);
CREATE TABLE leg_player_stats (
    stat_id BIGSERIAL PRIMARY KEY,
    leg_id BIGINT NOT NULL REFERENCES legs(leg_id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    position INTEGER NOT NULL CHECK (position >= 0),
    points INTEGER NOT NULL CHECK (points >= 0),
    turn INTEGER NOT NULL DEFAULT 0 CHECK (turn >= 0),
    triple20s INTEGER NOT NULL DEFAULT 0,
    bullseyes INTEGER NOT NULL DEFAULT 0,
    highest_score INTEGER NOT NULL DEFAULT 0,
    highest_checkout INTEGER NOT NULL DEFAULT 0,
    checkout_accuracy REAL NOT NULL DEFAULT 0,
    UNIQUE (leg_id, user_id),
    UNIQUE (leg_id, position)
);
