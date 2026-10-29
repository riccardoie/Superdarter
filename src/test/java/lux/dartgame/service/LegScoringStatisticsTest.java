package lux.dartgame.service;

import lux.dartgame.model.LegPlayerStat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class LegScoringStatisticsTest {
    @ParameterizedTest
    @CsvSource({
        "40, '20 2 20 3', 1", // Ignore darts after checkout.
        "40, '20 1 20 3 20 3', 3", // A bust still consumes a full turn.
        "501, '', 3",
        "501, '20 3', 3",
        "501, '0 1 20 3', 3",
        "40, '0 1 20 2', 2",
        "40, '0 1 0 1 20 2', 3"
    })
    void countsFullTurnsExceptForSuccessfulCheckouts(final int points, final String score, final int darts) {
        assertThat(CountdownRules.evaluate(points, score).dartsThrown()).isEqualTo(darts);
    }

    @Test
    void firstNineStopsAtNinthDartAndBustsScoreZero() {
        var result = CountdownRules.evaluate(501, "20 3 19 3 18 3", 1);
        assertThat(result.firstNineDarts()).isEqualTo(1);
        assertThat(result.firstNinePoints()).isEqualTo(60);
        assertThat(CountdownRules.evaluate(40, "20 1 20 3", 1).firstNinePoints()).isZero();
        assertThat(CountdownRules.evaluate(501, "20 3", 0).firstNinePoints()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
        "'20 2', 40", "'20 2 19 1', 40", "'20 3', 60", "'20 3 19 1', 60",
        "'20 3 20 1', 80", "'20 3 20 2', 100", "'20 3 20 3', 120",
        "'20 3 20 3 20 1', 140", "'20 3 20 3 20 2', 160",
        "'20 3 20 3 20 3', 180"
    })
    void countsExclusiveBands(final String score, final int band) {
        var stat = new LegPlayerStat();
        LegScoringStatistics.record(stat, CountdownRules.evaluate(501, score));
        int[] counts = {stat.getScore40(), stat.getScore60(), stat.getScore80(),
                stat.getScore100(), stat.getScore120(), stat.getScore140(),
                stat.getScore160(), stat.getScore180()};
        for (int i = 0; i < counts.length; i++) {
            assertThat(counts[i]).isEqualTo(band == 40 + i * 20 ? 1 : 0);
        }
    }

    @Test
    void bustDoesNotCountAs180AndLegacyStatsStayUnavailable() {
        var stat = new LegPlayerStat();
        LegScoringStatistics.record(stat, CountdownRules.evaluate(60, "20 3 20 3 20 3"));
        assertThat(stat.getScore180()).isZero();
        assertThat(stat.getDartsThrown()).isEqualTo(3);
        assertThat(stat.getFirstNinePoints()).isZero();
        stat.setScoringStatsAvailable(false);
        LegScoringStatistics.record(stat, CountdownRules.evaluate(501, "20 3"));
        assertThat(stat.getDartsThrown()).isEqualTo(3);
        assertThat(stat.isScoringStatsAvailable()).isFalse();
    }

    @Test
    void omittedMissesContributeToFirstNineAverage() {
        var stat = new LegPlayerStat();
        for (int round = 0; round < 3; round++) {
            LegScoringStatistics.record(stat,
                    CountdownRules.evaluate(501, "20 3", 9 - stat.getFirstNineDarts()));
        }
        assertThat(stat.getDartsThrown()).isEqualTo(9);
        assertThat(stat.getFirstNineDarts()).isEqualTo(9);
        assertThat(stat.getFirstNinePoints()).isEqualTo(180);
        assertThat(stat.getFirstNinePoints() * 3f / stat.getFirstNineDarts()).isEqualTo(60f);
        LegScoringStatistics.record(stat, CountdownRules.evaluate(501, "20 3", 0));
        assertThat(stat.getDartsThrown()).isEqualTo(12);
        assertThat(stat.getFirstNinePoints()).isEqualTo(180);
        assertThat(stat.getFirstNineDarts()).isEqualTo(9);
    }
}
