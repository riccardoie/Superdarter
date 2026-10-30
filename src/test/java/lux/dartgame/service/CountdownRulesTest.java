package lux.dartgame.service;

import lux.dartgame.exception.InvalidScoreException;
import java.util.Map;
import lux.dartgame.model.BoardHitCounts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CountdownRulesTest {
    @Test
    void boardHitsCountDartsNotPointsAndExcludeMisses() {
        for (int number = 1; number <= 20; number++) {
            for (int multiplier = 1; multiplier <= 3; multiplier++) {
                assertThat(CountdownRules.evaluate(501, number + " " + multiplier).boardHits())
                        .isEqualTo(Map.of(number, new BoardHitCounts(multiplier == 1 ? 1 : 0,
                                multiplier == 2 ? 1 : 0, multiplier == 3 ? 1 : 0)));
            }
        }
        assertThat(CountdownRules.evaluate(501, "20 1 20 2 20 3").boardHits())
                .isEqualTo(Map.of(20, new BoardHitCounts(1, 1, 1)));
        assertThat(CountdownRules.evaluate(501, "25 1 25 2 0 1").boardHits())
                .isEqualTo(Map.of(25, new BoardHitCounts(1, 1, 0)));
        assertThat(CountdownRules.evaluate(501, "").boardHits()).isEmpty();
        assertThat(CountdownRules.evaluate(501, "0 1 0 1 0 1").boardHits()).isEmpty();
    }

    @Test
    void boardHitsExcludeEntireBustedTurnsButIncludeCheckout() {
        assertThat(CountdownRules.evaluate(40, "20 1 20 3 25 2").boardHits())
                .isEmpty();
        assertThat(CountdownRules.evaluate(40, "20 2 25 2 19 3").boardHits())
                .isEqualTo(Map.of(20, new BoardHitCounts(0, 1, 0)));
        assertThat(CountdownRules.evaluate(20, "20 1 25 2", 9, false).boardHits())
                .isEqualTo(Map.of(20, new BoardHitCounts(1, 0, 0)));
    }

    @ParameterizedTest
    @CsvSource({
        "40, '20 1 20 3', true",
        "40, '20 1 19 1', true",
        "40, '20 1 20 1', true",
        "25, '25 1', true",
        "40, '20 1 20 3', false"
    })
    void allBustReasonsDiscardEveryBoardHit(final int points, final String score,
                                            final boolean doubleOut) {
        var result = CountdownRules.evaluate(points, score, 9, doubleOut);
        assertThat(result.bust()).isTrue();
        assertThat(result.boardHits()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "40, '20 2', 0, true, false, 0, 0",
        "50, '25 2', 0, true, false, 0, 1",
        "40, '0 1 20 2', 0, true, false, 0, 0",
        "40, '20 2 20 3 25 2', 0, true, false, 0, 0",
        "20, '20 1', 20, false, true, 0, 0",
        "60, '20 3', 60, false, true, 1, 0",
        "25, '25 1', 25, false, true, 0, 1",
        "21, '20 1', 21, false, true, 0, 0",
        "40, '20 1 19 1', 40, false, true, 0, 0",
        "40, '20 1 20 3 25 2', 40, false, true, 1, 0",
        "100, '20 3 20 2', 0, true, false, 1, 0",
        "501, '20 3 20 3 20 3', 321, false, false, 3, 0",
        "301, '20 3', 241, false, false, 1, 0",
        "501, '0 1', 501, false, false, 0, 0"
    })
    void evaluatesOrderedDarts(final int points, final String score, final int remaining,
                              final boolean won, final boolean bust, final int triples,
                              final int bulls) {
        var result = CountdownRules.evaluate(points, score);
        assertThat(result.remaining()).isEqualTo(remaining);
        assertThat(result.won()).isEqualTo(won);
        assertThat(result.bust()).isEqualTo(bust);
        assertThat(result.triples()).isEqualTo(triples);
        assertThat(result.bullseyes()).isEqualTo(bulls);
        assertThat(result.scored()).isEqualTo(points - remaining);
    }

    @ParameterizedTest
    @ValueSource(strings = {"20 3 20 3 20 3 1 1", "-20 2", "20 -2", "0 2",
            "25 3", "21 1", "20", "abc", "20 2 garbage", "999999999999999 1"})
    void rejectsInvalidDarts(final String score) {
        assertThatThrownBy(() -> CountdownRules.evaluate(301, score))
                .isInstanceOf(InvalidScoreException.class);
    }

    @ParameterizedTest
    @CsvSource({
        "20, '20 1', 0, true, false, 1",
        "60, '20 3', 0, true, false, 1",
        "25, '25 1', 0, true, false, 1",
        "40, '20 2', 0, true, false, 1",
        "50, '25 2', 0, true, false, 1",
        "21, '20 1', 1, false, false, 3",
        "21, '20 1 1 1', 0, true, false, 2",
        "1, '1 1', 0, true, false, 1",
        "20, '20 1 20 3', 0, true, false, 1",
        "40, '20 1 20 3', 40, false, true, 3",
        "21, '20 1 2 1', 21, false, true, 3"
    })
    void singleOutRequiresOnlyExactZero(final int points, final String score, final int remaining,
                                       final boolean won, final boolean bust, final int darts) {
        var result = CountdownRules.evaluate(points, score, 9, false);
        assertThat(result.remaining()).isEqualTo(remaining);
        assertThat(result.won()).isEqualTo(won);
        assertThat(result.bust()).isEqualTo(bust);
        assertThat(result.scored()).isEqualTo(points - remaining);
        assertThat(result.dartsThrown()).isEqualTo(darts);
        assertThat(result.firstNinePoints()).isEqualTo(points - remaining);
        assertThat(result.firstNineDarts()).isEqualTo(darts);
    }

    @Test
    void emptyRoundMeansAllMisses() {
        assertThat(CountdownRules.evaluate(301, "").remaining()).isEqualTo(301);
        assertThat(CountdownRules.evaluate(501, "   ").scored()).isZero();
        assertThatThrownBy(() -> CountdownRules.evaluate(301, null))
                .isInstanceOf(InvalidScoreException.class);
    }
}
