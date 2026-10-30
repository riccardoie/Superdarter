package lux.dartgame.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import lux.dartgame.model.MatchStatus;

/** Full reloadable game state; gameId is the player-facing match identifier. */
public record MatchResponse(long gameId, String owner, String gametype, int startingPoints,
                            MatchStatus status, Instant createdAt, Instant startedAt,
                            Instant finishedAt, String winner, String turn, long revision,
                            long currentLegId, String format, List<Player> players,
                            List<LegSummary> legs, Permissions permissions, int legsToWin) {
    public record Player(String username, int position, int points, int turns,
                         int bullseyes, int triple20s, int highestScore, int highestCheckout,
                            long legsWon, ScoringStats scoringStats, boolean guest,
                            Map<Integer, Integer> cricketMarks,
                            Map<Integer, BoardHitResponse> boardHits) {
    }

    public record ScoringStats(int dartsThrown, int firstNinePoints, int firstNineDarts,
                               float firstNineAverage, int score40, int score60, int score80,
                               int score100, int score120, int score140, int score160,
                               int score180) {
    }

    public record LegSummary(long legId, int number, MatchStatus status, String winner,
                             List<Player> players) {
    }

    public record Permissions(boolean canStart, boolean canScore,
                              boolean canDelete, boolean canAbandon, boolean canUndo,
                              boolean canRematch) {
    }
}
