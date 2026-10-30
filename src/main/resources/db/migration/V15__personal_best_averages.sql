ALTER TABLE playerstats
    ADD COLUMN highest_leg_average REAL CHECK (highest_leg_average >= 0),
    ADD COLUMN highest_game_average REAL CHECK (highest_game_average >= 0),
    ADD COLUMN highest_first_nine_average REAL CHECK (highest_first_nine_average >= 0);

-- Leg/game averages match the existing points-per-turn calculation. First nine
-- uses its recorded dart denominator; unknown legacy opening darts stay unknown.
WITH countdown_legs AS (
    SELECT s.user_id, m.match_id, m.status AS match_status, s.turn,
           CASE WHEN g.gametype IN ('301', '301 Single Out') THEN 301 ELSE 501 END
               - s.points AS scored,
           s.scoring_stats_available, s.first_nine_points, s.first_nine_darts
    FROM leg_player_stats s
    JOIN legs l ON l.leg_id = s.leg_id
    JOIN matches m ON m.match_id = l.match_id
    JOIN gametypes g ON g.gametype_id = m.gametype_id
    WHERE s.user_id IS NOT NULL AND l.status = 'COMPLETED'
      AND g.gametype IN ('301', '501', '301 Single Out', '501 Single Out')
), leg_bests AS (
    SELECT user_id,
           MAX(scored::DOUBLE PRECISION / NULLIF(turn, 0)) AS leg_average,
           MAX(CASE WHEN scoring_stats_available AND first_nine_darts > 0
               THEN first_nine_points::DOUBLE PRECISION * 3 / first_nine_darts END) AS first_nine
    FROM countdown_legs GROUP BY user_id
), game_averages AS (
    SELECT user_id, match_id, SUM(scored)::DOUBLE PRECISION / NULLIF(SUM(turn), 0) AS average
    FROM countdown_legs WHERE match_status = 'COMPLETED' GROUP BY user_id, match_id
), game_bests AS (
    SELECT user_id, MAX(average) AS game_average FROM game_averages GROUP BY user_id
)
UPDATE playerstats p SET highest_leg_average = l.leg_average,
    highest_first_nine_average = l.first_nine,
    highest_game_average = g.game_average
FROM leg_bests l LEFT JOIN game_bests g ON g.user_id = l.user_id
WHERE p.player_id = l.user_id;
