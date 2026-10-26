package lux.dartgame.dto;

import java.time.Instant;
import java.util.List;
import lux.dartgame.model.MatchStatus;

/** Full reloadable game state; gameId is the player-facing match identifier. */
public record MatchResponse(long gameId, String owner, String gametype, int startingPoints,
                            MatchStatus status, Instant createdAt, Instant startedAt,
                            Instant finishedAt, String winner, String turn, long revision,
                            long currentLegId, String format, List<Player> players,
                            List<LegSummary> legs, Permissions permissions, int legsToWin) {
    public record Player(String username, int position, int points, int turns,
                         int bullseyes, int triple20s, int highestScore, int highestCheckout,
                         float checkoutAccuracy, long legsWon) {
    }

    public record LegSummary(long legId, int number, MatchStatus status, String winner,
                             List<Player> players) {
    }

    public record Permissions(boolean canStart, boolean canScore,
                              boolean canDelete, boolean canAbandon) {
    }
}
