# Around the Clock

Hit the current target, starting at 1 and proceeding to 20, then bull.
Only the exact required board number advances progress. A single advances one
number, a double two and a treble three: D1 makes 3 the next target, T1 makes 4.
Advances past 20 stop at bull. T18, D19 and any hit on the required 20 still
require a **separate** bull dart. Outer bull and bullseye both finish. Early bull
hits and wrong numbers do not advance progress. Multipliers are automatic;
there is no allocation popup.

## Redemption and ties

After the first finish, only later players in that leg's throwing round may
redeem, and only if they can finish within three darts. With perfect trebles,
targets 15–20 or bull are eligible; target 14 needs four darts including bull.
Earlier players get no extra turn. Eligible players get one final turn.

Tied finishers throw for bull. A unique best region wins. Equal outer bulls or
bullseyes automatically trigger another throw for the tied best players. If all
remaining best throws are outside bull, compare physical distances and select
the closest player, or request a rethrow when still tied.

## Setup, corrections and statistics

Choose Around the Clock, then the leg count. Supports solo play, registered
friends, guests, a four-player maximum, first-to legs, randomized initial
starters, rotating subsequent starters and rematches. New legs start at 1.
Text and dartboard inputs share the same rules, live target preview and finish
confirmation. Redemption and tiebreaker transitions show brief animations.

V17 adds the gametype and leg/round JSON state. `MatchResponse.aroundClock`
contains the phase, progress (`marks[position].NEXT`), redemption queue and
bull-off history. Player `points` mirrors the next target: 1–20, 21 for bull,
0 for finished. It is not a countdown score. State is persisted and reloadable;
undo restores targets, turn order, redemption, bull-off attempts, wins and hits.
The revision-checked `/bull-off` endpoint resolves outside-bull distance ties.

Every unfinished submitted turn consumes three darts, including blank misses.
Successful finishes stop on the bull dart; later darts are ignored after full
input validation. Physical hits, even on wrong targets, appear on heat maps;
misses do not. Bull-off throws count as darts; distance decisions do not.
Leg/game wins and participation count normally. Countdown averages, opening
averages, high-score/checkout records and personal-best averages exclude this mode.
