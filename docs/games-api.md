# Standalone games API

The UI calls a `Match` a **game**. Its ID is exposed as `gameId`. One `Leg` is a
single countdown from 301 or 501. Choose `legsToWin` from 1–99. A target of 1 is
`SINGLE_LEG`; larger targets use `FIRST_TO_LEGS`. Sets are not implemented yet.

All endpoints require a bearer token. Membership comes from that token, not a
client-supplied username query. Lists include games where the user participates.
An administrator can inspect another user's game by ID.

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/games` | Create a pending game and its first leg |
| GET | `/api/games?gametype=501` | List participating games (filter optional) |
| GET | `/api/games/{id}` | Full reloadable state |
| POST | `/api/games/{id}/start` | Start, or resume an already-started game |
| POST | `/api/games/{id}/rounds` | Score the next player's round |
| POST | `/api/games/{id}/abandon` | End an in-progress game without a winner |
| DELETE | `/api/games/{id}` | Delete a pending game |

## Create

```json
{
  "gametype": "501",
  "legsToWin": 3,
  "players": [{ "username": "bob" }]
}
```

The authenticated owner is automatically added once and starts first. Other
players retain request order. Duplicate names are deduplicated. There may be at
most **10 distinct participants including the owner**. Omit `players` or pass `[]`
for solo play. All usernames must already exist. The roster cannot be edited.
Playable modes are `301`, `501`, `301 Single Out`, and `501 Single Out`;
unknown/unsupported modes return 404. The original `301` and `501` require a
double to finish. Single Out modes allow any valid dart to reach exactly zero.

Omitting `legsToWin` (or passing null) defaults to 1 for backward compatibility.
Zero, negative, and targets above 99 return 400. The target is fixed for the match.
Creation returns 201 and complete state. It does not begin play automatically.

## State

The same response shape is returned by create, get, start, round, and abandon:

```json
{
  "gameId": 12,
  "owner": "alice",
  "gametype": "501",
  "startingPoints": 501,
  "status": "IN_PROGRESS",
  "createdAt": "2026-10-26T12:00:00Z",
  "startedAt": "2026-10-26T12:00:05Z",
  "finishedAt": null,
  "winner": null,
  "turn": "alice",
  "revision": 1,
  "currentLegId": 20,
  "format": "SINGLE_LEG",
  "legsToWin": 1,
  "players": [{
    "username": "alice", "position": 0, "points": 501, "turns": 0,
    "bullseyes": 0, "triple20s": 0, "highestScore": 0,
    "highestCheckout": 0, "legsWon": 0
  }],
  "legs": [{ "legId": 20, "number": 1, "status": "IN_PROGRESS", "winner": null,
    "players": [{ "username": "alice", "position": 0, "points": 501, "turns": 0,
      "bullseyes": 0, "triple20s": 0, "highestScore": 0, "highestCheckout": 0,
      "legsWon": 0 }] }],
  "permissions": { "canStart": false, "canScore": true, "canDelete": false, "canAbandon": true }
}
```

`players` contains every participant in starting order. `turn` is null after the
game ends. `winner` is set only on normal completion. Permissions are computed for
the caller; do not infer ownership by querying another list or relying on a cache.
`players` at the top level describes the current/final leg, not aggregated match
performance. `legs[].players` preserves each leg's statistics. `legsWon` is the
participant's overall standing, derived from completed legs.

## First-to progression

After a valid checkout, the leg is completed and its winner gains one leg win. If that
player has reached `legsToWin`, the match completes. Otherwise a new leg is created
and started in the same transaction. Remaining scores, turns, and leg-specific
statistics reset; previous legs stay intact. All legs use the selected mode's rules.

The starter rotates through the original participant order: first participant in
leg 1, second in leg 2, and so on, wrapping around. Rotation is independent of who
won the previous leg. Solo matches also support first-to targets.

The round response may have a **new currentLegId**, reset player scores, and the
next starter. Always replace client state with that response. A repeated request
using the old leg ID/revision remains a 409 and cannot create another leg.

## Score a round

```json
{ "username": "alice", "score": "20 3 20 2", "legId": 20, "revision": 1 }
```

Any participant can record the next player's throws for shared-device play.
Administrators who are not participants cannot score or start games.

- `score` contains 0–3 ordered number/multiplier pairs. `""` means all misses.
- `0 1` explicitly records a miss. Unthrown darts need not be supplied.
- The whole turn busts on a negative remaining score in every mode.
- In `301` and `501`, leaving 1 or reaching zero without a double also busts.
- In Single Out modes, leaving 1 is allowed and any valid dart can finish at zero.
- Processing stops at a checkout or bust. Later supplied darts do not affect stats.
- Busts advance the turn but preserve the turn's initial score.
- Highest round score uses credited points (zero on bust); hit counts include
  actual hits through the bust/checkout dart. Highest checkout is the winning turn's score.

Each command locks the match row. A round must supply the **current revision and
current leg ID**. A stale or repeated submission returns **409** without scoring
again, even in solo play. Fetch current state and ask the user to re-enter; never
retry a round automatically using a new revision. Start is idempotent while in progress.

## Lifecycle, history, statistics

```text
PENDING --start--> IN_PROGRESS --checkout----> COMPLETED
                              --abandon----> ABANDONED
PENDING --delete--> removed
```

Only the owner or an administrator can delete/abandon. Completed and abandoned
history cannot be deleted, restarted, or scored. Repeating abandon is idempotent.
Pending deletion returns 204 and cascades only the game's rows, not users/stats.

Each completed leg increments leg counters once for every participant. Only its
winner receives a leg win. Match counters increment for all participants only when
someone reaches the target, with a match win only for the overall winner.
Completed leg results/totals remain counted if the match is later abandoned;
the unfinished leg and abandoned match contribute no cumulative counters.
Averages retain the existing average of each
completed leg's points-per-turn average; zero-turn participants contribute zero.
Checkout-accuracy calculation is unchanged (not yet implemented from dart attempts).

Player profile fields now use `playedMatches`, `wonMatches`, `playedLegs`, and
`wonLegs` instead of the ambiguous `playedGames`/`wonGames`.

Validation returns 400, inaccessible games 403, missing games/users/types 404,
stale or invalid lifecycle transitions 409, and malformed dart scores 406.
Legacy `/api/session` endpoints and `/session/...` frontend routes are removed.
