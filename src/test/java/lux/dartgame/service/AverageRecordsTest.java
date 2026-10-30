package lux.dartgame.service;

import java.util.List;
import lux.dartgame.model.Gametype;
import lux.dartgame.model.Leg;
import lux.dartgame.model.LegPlayerStat;
import lux.dartgame.model.Match;
import lux.dartgame.model.MatchStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AverageRecordsTest {
    @Test
    void bestGameAverageWeightsLegsByTurnsInsteadOfAveragingLegAverages() {
        Match game = game("301", MatchStatus.COMPLETED);
        LegPlayerStat first = leg(game, MatchStatus.COMPLETED, 0, 3, 300, 9);
        LegPlayerStat second = leg(game, MatchStatus.COMPLETED, 241, 1, 60, 3);
        AverageRecords records = AverageRecords.calculate(List.of(first, second));
        assertThat(records.legAverage()).isCloseTo(301f / 3, within(0.001f));
        assertThat(records.gameAverage()).isEqualTo(90.25f);
        assertThat(records.firstNineAverage()).isEqualTo(100f);
    }

    @Test
    void recordsCanComeFromDifferentGamesAndIncludeLosingPlayers() {
        LegPlayerStat first = leg(game("301", MatchStatus.COMPLETED), MatchStatus.COMPLETED,
                1, 2, 300, 6);
        LegPlayerStat second = leg(game("501", MatchStatus.COMPLETED), MatchStatus.COMPLETED,
                141, 3, 360, 9);
        second.setFirstNinePoints(450);
        AverageRecords records = AverageRecords.calculate(List.of(first, second));
        assertThat(records.legAverage()).isEqualTo(150f);
        assertThat(records.gameAverage()).isEqualTo(150f);
        assertThat(records.firstNineAverage()).isEqualTo(150f);
    }

    @Test
    void completedLegsInUnfinishedOrAbandonedGamesQualifyButNotForGameRecords() {
        for (MatchStatus status : List.of(MatchStatus.IN_PROGRESS, MatchStatus.ABANDONED)) {
            LegPlayerStat stat = leg(game("501", status), MatchStatus.COMPLETED, 201, 2, 300, 6);
            AverageRecords records = AverageRecords.calculate(List.of(stat));
            assertThat(records.legAverage()).isEqualTo(150f);
            assertThat(records.firstNineAverage()).isEqualTo(150f);
            assertThat(records.gameAverage()).isNull();
        }
    }

    @Test
    void unknownFirstNineDoesNotSuppressReliableLegAndGameAverages() {
        LegPlayerStat stat = leg(game("301", MatchStatus.COMPLETED), MatchStatus.COMPLETED,
                0, 3, 300, 9);
        stat.setScoringStatsAvailable(false);
        AverageRecords records = AverageRecords.calculate(List.of(stat));
        assertThat(records.firstNineAverage()).isNull();
        assertThat(records.legAverage()).isCloseTo(301f / 3, within(0.001f));
        assertThat(records.gameAverage()).isEqualTo(records.legAverage());
    }

    @Test
    void fewerThanNineOpeningDartsUseActualRecordedDenominator() {
        LegPlayerStat stat = leg(game("301", MatchStatus.COMPLETED), MatchStatus.COMPLETED,
                0, 3, 301, 7);
        assertThat(AverageRecords.calculate(List.of(stat)).firstNineAverage()).isEqualTo(129f);
    }

    @Test
    void cricketUnfinishedLegsAndUnplayedLegsNeverCreateRecords() {
        var cricket = leg(game("Cricket", MatchStatus.COMPLETED), MatchStatus.COMPLETED,
                0, 1, 540, 3);
        var pending = leg(game("501", MatchStatus.IN_PROGRESS), MatchStatus.IN_PROGRESS,
                141, 2, 360, 6);
        var unplayed = leg(game("501", MatchStatus.COMPLETED), MatchStatus.COMPLETED,
                501, 0, 0, 0);
        assertThat(AverageRecords.calculate(List.of(cricket, pending, unplayed)))
                .isEqualTo(new AverageRecords(null, null, null));
        assertThat(AverageRecords.calculate(List.of()))
                .isEqualTo(new AverageRecords(null, null, null));
    }

    @Test
    void zeroScoresAreValidAndRecordsNeverDropOnOrdinaryCompletion() {
        var stat = leg(game("501", MatchStatus.COMPLETED), MatchStatus.COMPLETED, 501, 1, 0, 3);
        assertThat(AverageRecords.calculate(List.of(stat))).isEqualTo(new AverageRecords(0f, 0f, 0f));
        assertThat(AverageRecords.best(null, 100f)).isEqualTo(100f);
        assertThat(AverageRecords.best(100f, null)).isEqualTo(100f);
        assertThat(AverageRecords.best(100f, 90f)).isEqualTo(100f);
        assertThat(AverageRecords.best(100f, 110f)).isEqualTo(110f);
    }

    private Match game(final String type, final MatchStatus status) {
        Gametype gametype = new Gametype();
        gametype.setGametype(type);
        Match game = new Match();
        game.setGametype(gametype);
        game.setStatus(status);
        return game;
    }

    private LegPlayerStat leg(final Match game, final MatchStatus status, final int points,
                               final int turns, final int openingPoints, final int openingDarts) {
        Leg leg = new Leg();
        leg.setMatch(game);
        leg.setStatus(status);
        LegPlayerStat stat = new LegPlayerStat();
        stat.setLeg(leg);
        stat.setPoints(points);
        stat.setTurn(turns);
        stat.setFirstNinePoints(openingPoints);
        stat.setFirstNineDarts(openingDarts);
        return stat;
    }
}
