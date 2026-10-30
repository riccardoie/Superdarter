package lux.dartgame.service;

import java.util.List;
import java.util.stream.Collectors;
import lux.dartgame.constants.Constants;
import lux.dartgame.model.LegPlayerStat;
import lux.dartgame.model.MatchStatus;

/** Personal bests over one player's retained, completed countdown history. */
public record AverageRecords(Float legAverage, Float gameAverage, Float firstNineAverage) {
    public static AverageRecords calculate(final List<LegPlayerStat> history) {
        var legs = history.stream().filter(stat ->
                stat.getLeg().getStatus() == MatchStatus.COMPLETED
                && !Constants.GAMETYPE_CRICKET.equals(
                        stat.getLeg().getMatch().getGametype().getGametype())).toList();
        Float leg = legs.stream().filter(stat -> stat.getTurn() > 0)
                .map(stat -> (float) scored(stat) / stat.getTurn())
                .max(Float::compare).orElse(null);
        Float firstNine = legs.stream().filter(stat -> stat.isScoringStatsAvailable()
                        && stat.getFirstNineDarts() > 0)
                .map(stat -> (float) stat.getFirstNinePoints() * Constants.MAX_DARTS_PER_TURN
                        / stat.getFirstNineDarts()).max(Float::compare).orElse(null);
        Float game = legs.stream()
                .filter(stat -> stat.getLeg().getMatch().getStatus() == MatchStatus.COMPLETED)
                .collect(Collectors.groupingBy(stat -> stat.getLeg().getMatch())).values().stream()
                .filter(stats -> stats.stream().mapToLong(LegPlayerStat::getTurn).sum() > 0)
                .map(stats -> (float) stats.stream().mapToLong(AverageRecords::scored).sum()
                        / stats.stream().mapToLong(LegPlayerStat::getTurn).sum())
                .max(Float::compare).orElse(null);
        return new AverageRecords(leg, game, firstNine);
    }

    public static Float best(final Float previous, final Float candidate) {
        if (previous == null) {
            return candidate;
        }
        return candidate == null ? previous : Math.max(previous, candidate);
    }

    private static int scored(final LegPlayerStat stat) {
        return CountdownRules.startingPoints(stat.getLeg().getMatch().getGametype().getGametype())
                - stat.getPoints();
    }
}
