package lux.dartgame.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lux.dartgame.exception.InvalidScoreException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CricketRulesTest {
    private CricketRules.Player player(final int points, final Map<Integer, Integer> marks) {
        return new CricketRules.Player(points, marks);
    }

    @Test
    void targetsCloseWithSinglesDoublesAndTriples() {
        for (int number : CricketRules.TARGETS) {
            String score = number == 25 ? "25 1 25 2" : number + " 3";
            var result = CricketRules.evaluate(player(0, Map.of()), List.of(player(0, Map.of())), score);
            assertThat(result.marks()).containsEntry(number, 3);
            assertThat(result.scored()).isZero();
            assertThat(result.won()).isFalse();
        }
    }

    @Test
    void onlyExcessMarksScoreIncludingTheDartThatClosesTheTarget() {
        var marks = Map.of(20, 2);
        var result = CricketRules.evaluate(player(10, marks), List.of(player(0, Map.of())), "20 3");
        assertThat(result.marks()).containsEntry(20, 3);
        assertThat(result.scored()).isEqualTo(40);
        assertThat(result.points()).isEqualTo(50);
        assertThat(marks).containsEntry(20, 2);
        result = CricketRules.evaluate(player(0, Map.of(25, 2)),
                List.of(player(0, Map.of())), "25 2");
        assertThat(result.scored()).isEqualTo(25);
    }

    @Test
    void pointsAccumulateOnOpenOpponentsButNeverOnUniversallyClosedTargets() {
        var result = CricketRules.evaluate(player(0, Map.of(20, 3)),
                List.of(player(0, Map.of(20, 3)), player(0, Map.of(20, 2))), "20 3 20 2 20 1");
        assertThat(result.points()).isEqualTo(120);
        result = CricketRules.evaluate(player(0, Map.of(20, 3)),
                List.of(player(0, Map.of(20, 3))), "20 3");
        assertThat(result.points()).isZero();
    }

    @Test
    void nonTargetsAndMissesAddNoMarksOrPoints() {
        var result = CricketRules.evaluate(player(0, Map.of()), List.of(), "14 3 1 2 0 1");
        assertThat(result.points()).isZero();
        assertThat(result.marks()).isEmpty();
        assertThat(result.dartsThrown()).isEqualTo(3);
        assertThat(result.boardHits()).containsKeys(14, 1).doesNotContainKey(0);
        assertThat(CricketRules.evaluate(player(0, Map.of()), List.of(), "").dartsThrown()).isEqualTo(3);
    }

    @Test
    void closingAllTargetsWhileBehindDoesNotWinButCatchingUpDoes() {
        Map<Integer, Integer> marks = closed();
        marks.put(25, 1);
        var result = CricketRules.evaluate(player(0, marks), List.of(player(25, Map.of())), "25 2");
        assertThat(result.won()).isFalse();
        result = CricketRules.evaluate(player(0, result.marks()), List.of(player(25, Map.of())),
                "25 1 20 3");
        assertThat(result.won()).isTrue();
        assertThat(result.points()).isEqualTo(25);
        assertThat(result.dartsThrown()).isEqualTo(1);
        assertThat(result.boardHits()).doesNotContainKey(20);
    }

    @Test
    void winningRequiresEveryTargetAndEnoughPointsAgainstEveryOpponent() {
        var result = CricketRules.evaluate(player(100, Map.of()), List.of(player(0, Map.of())), "20 3");
        assertThat(result.won()).isFalse();
        var marks = closed();
        marks.put(15, 2);
        result = CricketRules.evaluate(player(20, marks),
                List.of(player(10, Map.of()), player(21, Map.of())), "15 1");
        assertThat(result.won()).isFalse();
        result = CricketRules.evaluate(player(21, marks),
                List.of(player(10, Map.of()), player(21, Map.of())), "15 1");
        assertThat(result.won()).isTrue();
    }

    @Test
    void soloClosesAllTargetsWithoutScoringAndStopsAtTheWinningDart() {
        var marks = closed();
        marks.put(25, 2);
        var result = CricketRules.evaluate(player(0, marks), List.of(), "25 2 20 3");
        assertThat(result.won()).isTrue();
        assertThat(result.points()).isZero();
        assertThat(result.dartsThrown()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"25 3", "0 2", "21 1", "20", "20 3 20 3 20 3 20 3", "abc"})
    void invalidInputsCannotChangeMarks(final String score) {
        var marks = Map.of(20, 1);
        assertThatThrownBy(() -> CricketRules.evaluate(player(0, marks), List.of(), score))
                .isInstanceOf(InvalidScoreException.class);
        assertThat(marks).containsEntry(20, 1);
    }

    private Map<Integer, Integer> closed() {
        Map<Integer, Integer> result = new HashMap<>();
        CricketRules.TARGETS.forEach(number -> result.put(number, 3));
        return result;
    }
}
