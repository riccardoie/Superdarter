# Mikke Mus

Two playable modes are added by V16: `Mikke Mus Forced` and `Mikke Mus Relaxed`.
Both support registered players, guests, solo practice, first-to-leg formats,
random first starters, rotating subsequent starters, per-leg/game heat maps,
undo and rematches. There are no points, busts or countdown finishing rules.
Choose **Mikke Mus** when creating a game, then select **Forced** or **Relaxed**
on the setup page with the leg count.

## Marks and allocations

Close 20, 19, 18, 17, 16, 15, 14, doubles, trebles and bull with three marks each.
Marks are capped at three. A double/treble can be assigned to **one** category:
its board number (two/three marks), or doubles/trebles (one mark). A dart cannot
split between categories, and excess marks do not carry over. Outer bull adds
one bull mark and bullseye two. Bullseye counts only toward bull, never doubles.
Non-target hits, or hits on locked targets, add no
marks but remain actual recorded board hits. Misses add none.
Double and treble entries open an on-screen category popup, except bullseye,
which always counts toward bull. Text-number edits prompt when leaving the number field.
If the number is already closed, the dart automatically counts toward
doubles/trebles without a popup, including closures earlier in the current turn.
If doubles/trebles are closed, the dart automatically counts toward its number.
Forced-mode restrictions still apply to whether that category gains a mark.
When submitted throws close all targets, a popup asks to confirm the player's
finish before saving. This does not skip redemption or bull-offs. During play,
Mikke Mus player “More stats” panels appear below the Undo/Register controls.
Use the dart's category button to change a choice. “Decide later” keeps the dart
unresolved; registering the round asks again before anything can be submitted.

- **Relaxed:** targets can close in any order.
- **Forced:** numbered targets must close in descending order, 20 through 14.
  While a numbered target remains open, a dart assigned to doubles/trebles must
  also hit that currently required number. After all numbered targets close,
  any appropriate double/treble can count. Bull marks unlock only when all
  numbered targets **and** doubles **and** trebles are closed.

The round endpoint accepts optional `allocations`, one per submitted dart:
`NUMBER`, `DOUBLE`, `TREBLE`, or `BULL`. Without allocations, numbered darts
default to their number and bull defaults to bull marks. Blank slots are misses.
Impossible multiplier/category choices reject the entire submission. A valid
allocation to a locked target simply contributes no marks. Processing stops on
the finishing dart; later darts are ignored after validation.

## Redemption

The first finisher does not automatically win if somebody later in that same
throwing round can still finish. Only later positions in that leg's order may
redeem, and only if their remaining targets can close within three darts.
Earlier players do not get an extra turn. The minimum is one dart per open
number, one per missing double/treble mark, and one per two missing bull marks.
Forced ordering does not increase that minimum: doubles/trebles can be hit on
the current number before closing it, and bull is completed last.

Eligible players receive one final turn. A miss can spoil redemption; it never
grants another turn. Ineligible players are skipped without inventing darts.
If only one player finishes, that player wins the leg. Solo games finish directly.

## Bull-off

Tied finishers throw one dart each in their existing order. Record each throw as
outside bull, outer bull or bullseye. A unique best region wins automatically.
If the best throws tie in outer bull or bullseye, the tied players automatically
throw again. Compare physical distances only when the best throws are outside
bull: select the closest player, or request another throw if still tied.
Only the best-region tied players remain eligible.

Bull-off history records each player's region and attempt. Real bull-off darts
count toward darts thrown and bull hits; outside-bull entries have no exact board
location, so no board number is invented for the heat map. Distance decisions
and rethrow requests add no darts or hits. Countdown averages, opening averages,
highest scores and checkout records exclude both Mikke Mus modes.

## State and corrections

Leg JSON state stores all marks, phase, current position, redemption queue,
finishers, bull-off candidates and throws. `MatchResponse.mikkeMus` exposes the
current leg's state, and each player's `mikkeMarks` exposes their leg-specific
marks, including historical legs. New legs and rematches have fresh state.

`POST /api/games/{id}/bull-off` takes `legId`, `revision` and either `winner` or
`rethrow: true`. Registered participants can score for guests, as in other modes.
The match lock, participant permissions and revision checks apply to every action.
Each scoring/bull-off/decision action stores a full pre-action state snapshot.
Undo restores marks, phase, queues, throw history, counters and heat-map counts,
including reopening a resolved leg or match. No records from old games are changed.
