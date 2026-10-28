-- Guests belong only to their match/legs, never to users or profile statistics.
ALTER TABLE match_participants ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE match_participants ADD COLUMN guest_name VARCHAR(255);
ALTER TABLE match_participants ADD CONSTRAINT participant_identity_check
    CHECK ((user_id IS NOT NULL) <> (guest_name IS NOT NULL));
ALTER TABLE match_participants ADD CONSTRAINT participant_guest_name_check
    CHECK (guest_name IS NULL OR (guest_name = btrim(guest_name) AND guest_name <> ''));
ALTER TABLE match_participants ADD CONSTRAINT participant_guest_unique UNIQUE (match_id, guest_name);

ALTER TABLE leg_player_stats ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE leg_player_stats ADD COLUMN guest_name VARCHAR(255);
ALTER TABLE leg_player_stats ADD CONSTRAINT leg_player_identity_check
    CHECK ((user_id IS NOT NULL) <> (guest_name IS NOT NULL));
ALTER TABLE leg_player_stats ADD CONSTRAINT leg_player_guest_name_check
    CHECK (guest_name IS NULL OR (guest_name = btrim(guest_name) AND guest_name <> ''));
ALTER TABLE leg_player_stats ADD CONSTRAINT leg_guest_unique UNIQUE (leg_id, guest_name);

ALTER TABLE matches ADD COLUMN winner_guest_name VARCHAR(255);
ALTER TABLE matches DROP CONSTRAINT matches_check;
ALTER TABLE matches ADD CONSTRAINT match_completed_winner_check
    CHECK ((status = 'COMPLETED') = (winner_id IS NOT NULL OR winner_guest_name IS NOT NULL));
ALTER TABLE matches ADD CONSTRAINT match_winner_identity_check
    CHECK (winner_id IS NULL OR winner_guest_name IS NULL);

ALTER TABLE legs ADD COLUMN winner_guest_name VARCHAR(255);
ALTER TABLE legs DROP CONSTRAINT legs_check;
ALTER TABLE legs ADD CONSTRAINT leg_completed_winner_check
    CHECK ((status = 'COMPLETED') = (winner_id IS NOT NULL OR winner_guest_name IS NOT NULL));
ALTER TABLE legs ADD CONSTRAINT leg_winner_identity_check
    CHECK (winner_id IS NULL OR winner_guest_name IS NULL);
