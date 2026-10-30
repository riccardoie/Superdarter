# Personal-best averages

V15 adds three nullable records to `playerstats` and the profile statistics API:

| API field | Meaning |
| --- | --- |
| `highestLegAverage` | Highest points scored / turns in a completed countdown leg. |
| `highestGameAverage` | Highest total points scored / total turns across all legs of a completed countdown game. |
| `highestFirstNineAverage` | Highest recorded opening points × 3 / opening darts in a completed countdown leg. |

Leg and game averages use the same points-per-turn calculation as the existing
scoreboard; the game record is turn-weighted, not an unweighted mean of leg
averages. First-nine counters use up to nine recorded darts and the existing
short-leg denominator when a player finishes with fewer than nine darts.

Both winners and losers qualify. Completed legs in retained abandoned or ongoing
games can set leg and opening records, but only completed games set game records.
Unfinished legs, players who have not thrown a turn, guests and cricket do not
create these profile records. Zero averages are valid when actual turns/darts
were recorded. A missing qualifying record is null and displays as “Not
available”, rather than inventing a zero or historical dart count.

Migration V15 backfills from retained completed countdown history. Historical leg
and game averages can use known turn counts even if detailed scoring is missing;
first-nine records require `scoring_stats_available` and a nonzero recorded dart
count. No dart-by-dart history is fabricated.

Records update transactionally when a leg/game completes, under the existing user
locks. Checkout undo rebuilds the records from the remaining qualifying history,
so undo can lower a best or make it unavailable again. Replaying a corrected round
can establish a new best. Profiles and comparisons show these values to two
decimal places without changing existing average points, score or checkout stats.
