# Detailed scoring statistics

Flyway V6 adds per-player, per-leg counters to `leg_player_stats`. No previous
migration is modified and existing games are retained.

The game API exposes these as `players[].scoringStats`, including in each leg's
player list. Match tables sum the leg counters; first-nine averages are weighted
by the number of first-nine darts, not averaged equally across legs.

- **Darts thrown:** every submitted turn counts as three darts, including busts.
  Blank dart fields count as misses, including blanks between entered darts.
  Only a successful checkout counts fewer darts, stopping at the winning dart.
- **First 9 avg:** points scored by the first nine counted darts in each leg,
  divided by those darts and multiplied by three. Short legs use the darts
  actually thrown. A busted turn scores zero but counts as three darts.
  Previously recorded counters are not recalculated when these rules change.
- **Score counts:** mutually exclusive turn totals of 40–59, 60–79, 80–99,
  100–119, 120–139, 140–159, 160–179, and exactly 180. Checkout turns count;
  busted turns do not. The UI retains the familiar `40+`, `60+`, etc. labels.
- **180:** counts complete 180-point turns. The existing `triple20s` field still
  counts individual triple-20 hits and is not used as a 180 counter.

Previously played legs cannot be reconstructed without a historical dart log.
V6 marks their detailed statistics unavailable (`scoringStats: null` in the API),
including if scoring resumes in that leg. New legs and existing unplayed player
rows start with zero counters. If any leg lacks detailed statistics, the match
table displays unavailable rather than misleading partial totals. Existing
turns, averages, highest scores, bullseyes, and results remain intact.

Statistics used in profile/lifetime totals are unchanged by this migration;
detailed match history is retained in the leg rows.
