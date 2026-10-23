-- Consolidated schema baseline for a new, empty database. No seed data.

CREATE TABLE roles (
    role_id BIGSERIAL PRIMARY KEY,
    role VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE users (
    user_id BIGSERIAL PRIMARY KEY,
    user_name VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    role_id BIGINT NOT NULL REFERENCES roles(role_id)
);

CREATE TABLE gametypes (
    gametype_id BIGSERIAL PRIMARY KEY,
    gametype VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE sessions (
    session_id BIGSERIAL PRIMARY KEY,
    is_active BOOLEAN NOT NULL,
    played_at TIMESTAMP,
    owner_id BIGINT NOT NULL REFERENCES users(user_id)
);

CREATE TABLE games (
    game_id BIGSERIAL PRIMARY KEY,
    played_at TIMESTAMP,
    session_id BIGINT NOT NULL REFERENCES sessions(session_id),
    winner_id BIGINT REFERENCES users(user_id),
    gametype_id BIGINT NOT NULL,
    nr_of_players INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_games_gametype FOREIGN KEY (gametype_id) REFERENCES gametypes(gametype_id)
);

CREATE TABLE session_mm_users (
    session_id BIGINT NOT NULL REFERENCES sessions(session_id),
    user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    PRIMARY KEY (session_id, user_id)
);

CREATE TABLE playerstats (
    player_stat_id BIGSERIAL PRIMARY KEY,
    avg_points REAL,
    won_games INTEGER,
    played_games INTEGER,
    triple20s INTEGER,
    bullseyes INTEGER,
    highest_score INTEGER,
    highest_checkout INTEGER NOT NULL,
    avg_checkout_accuracy REAL NOT NULL,
    player_id BIGINT UNIQUE NOT NULL REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE gamestats (
    user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    game_id BIGINT NOT NULL REFERENCES games(game_id) ON DELETE CASCADE,
    points INTEGER NOT NULL DEFAULT 0,
    turn INTEGER NOT NULL DEFAULT 0,
    position INTEGER NOT NULL DEFAULT 0,
    triple20s INTEGER,
    bullseyes INTEGER,
    highest_score INTEGER,
    checkout_accuracy REAL NOT NULL,
    highest_checkout INTEGER NOT NULL,
    PRIMARY KEY (user_id, game_id),
    CONSTRAINT gamestats_position_unique UNIQUE (game_id, position)
);
