# Board hit statistics

Flyway migration `V12__board_hit_counts.sql` adds `round_board_hits`, keyed by
submitted round and board number. Existing users, games, round snapshots, and
statistics are preserved. No old hits are inferred or backfilled: the original
dart-by-dart inputs were not stored, so the new counters cover recorded hits
starting with this deployment, including new rounds in already-running games.
Each row has separate `single_hits`, `double_hits`, and `triple_hits` columns;
their sum is the total number of hits on that number in the round.

## Counting rules

- Each actual hit on 1–20 or 25 counts once, independently of its multiplier.
  A single 20, double 20, and triple 20 in one round produce three hits on 20,
  not 120 hits. Both outer bull and bullseye count as hits on 25.
- Explicit misses (`0 1`), blank rounds, and omitted darts add no hits.
- Busted turns contribute no board hits, including any hits before the busting
  dart, because the entire turn's score is discarded.
- Darts entered after the busting or winning dart are ignored, just as they are
  for scoring. The same rules apply to single-out and double-out games.
- Invalid, unauthorized, or stale round submissions cannot change the counters.

## Persistence and corrections

Hit rows are saved in the same transaction as each scored round, rather than
waiting for the leg to finish. Storing per-round counts avoids shared counter
updates and lost increments when a player scores in multiple games concurrently.

Undo removes that round and its hit rows atomically, including checkout undo
and leg rollback. Replaying the corrected round records only the corrected hits.
New legs and rematches do not copy old hit rows.

Counts follow retained history. In-progress and completed games, and retained
abandoned games (including their unfinished leg), contribute. If the application
erases a game, its hit rows are removed by cascading deletion. Guest hit rows stay
with the round but never contribute to any registered account, even if the guest
display name matches an account username.

## API

Each `PlayerStatResponse` from `POST /api/playerStats` now includes `boardHits`:
a map of board numbers to `total`, `singles`, `doubles`, and `triples` counts.
It always contains 1–20 and 25; untouched numbers have zero counts. JSON represents
these numeric map keys as strings. For example:

```json
{
  "20": { "total": 7, "singles": 4, "doubles": 2, "triples": 1 },
  "25": { "total": 2, "singles": 1, "doubles": 1, "triples": 0 }
}
```

The database forbids negative counts, more than three hits per number in one
round, miss/invalid board numbers, and triple 25. The total is derived, rather than
stored independently, so multiplier counters and totals cannot drift apart.

The totals are available immediately during play and apply independently to both
players in a two-player statistics request. These are counts recorded since V12,
not a reconstruction of lifetime hits before the migration. Other profile
statistics retain their existing completed-leg semantics.

## Profile display

Own and friend profiles show a dartboard heat map and total recorded board hits,
with a Numbers option for a per-number single/double/triple breakdown. Comparison
mode shows each player's board separately. Colours use a shared linear scale
across all multiplier regions within each board, from no hits to its highest
region count. Each board's legend shows that maximum.

Both single regions use the same count because their locations are not recorded
separately; the total counts those darts only once. The outer bull shows single
25 and the inner bull shows double 25. Misses and busted turns are excluded
throughout. This rule applies to newly submitted rounds; previously recorded
counts are not recalculated.

## Game and leg heat maps

Each player in `MatchResponse.players` now includes `boardHits` for the current
leg. Each player in `legs[].players` includes the same breakdown for that specific
leg, with all 21 board numbers present. Counts are grouped by the leg-player
record within the requested match, including match-local guests. Profile totals
and other matches never contribute.

Player **More stats** panels show the heat map, Numbers switch and board-hit total.
Completed-leg panels use that leg's counts; overall game panels sum the player's
counts across all of the game's legs, including a retained unfinished leg in an
abandoned game. Cricket uses the same heat-map controls in its player details.
Existing bust, miss, winning-dart and undo rules apply unchanged. Rematches start
with empty maps. Games played before hit tracking have no inferred historical
hits. This display uses existing round-owned hit rows and needs no new migration.
