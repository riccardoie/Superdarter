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
- Hits up to and including a bust count, even though the round scores zero.
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
statistics retain their existing completed-leg semantics. No frontend display
changes are included.
