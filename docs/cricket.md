# Standard cricket

Select **Cricket** during game creation. It supports the same registered players,
guests, first-to-leg targets, randomized first starter, rotating subsequent
starters, undo, rematches and permissions as countdown games. No finishing rule
is selected. Solo practice ends when all targets are closed, without scoring
points against imaginary opponents.

## Rules

- Targets are 20, 19, 18, 17, 16, 15 and bull (25). Other numbers and misses do
  not add marks or points. There are no busts.
- Singles add one mark, doubles two, triples three. Bull permits only single
  and double. Three marks close a target; stored marks are capped at three.
- Excess marks, including excess on the dart that closes a target, score the
  target's value per mark if at least one opponent still has that target open.
  These points go to the thrower, not opponents. Targets closed by everyone
  cannot score.
- Close all seven targets and have at least as many points as every opponent
  to win. A closed player who is behind can continue scoring until caught up.
- Turns use three darts, including omitted misses. Winning ends the turn at the
  winning dart; any later submitted darts are ignored. Invalid input anywhere
  in the submitted round is rejected before any changes are made.

## Storage and statistics

V14 adds Cricket to the gametype list and seven mark columns to both leg player
state and round snapshots, preserving older game data with zero marks. The API
adds `players[].cricketMarks`, keyed by target (JSON string keys), for current and
historical legs. Non-cricket players return null for this field. `points` is the
accumulated cricket score, not points remaining; `startingPoints` is zero.

Undo restores marks, points, darts and board-hit counts together, including after
a winning round or transition to a new leg. Marks start at zero in new legs and
rematches. The match lock and revision checks apply unchanged.

Cricket contributes to played/won matches and legs, darts thrown, triple 20s,
bull hits and the board heat map. Actual board hits before victory are tracked,
including hits outside cricket targets; misses and darts after victory are not.
Countdown averages, highest round score and checkout records remain based on
countdown legs only, including when totals are rebuilt after undo. The cricket
scoreboard uses marks and accumulated points rather than countdown averages,
checkout suggestions or score-band counters.
