package lux.dartgame.service;

import java.util.List;
import java.util.Map;
import lux.dartgame.exception.InvalidScoreException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AroundClockRulesTest {
    @Test
    void requiredNumbersAdvanceByTheirMultiplierAndOtherHitsDoNotProgress() {
        var state = AroundClockRules.initial(1);
        var result = AroundClockRules.play(state, "2 3 1 2 3 3");
        assertThat(result.state().getMarks().get(0)).containsEntry("NEXT", 6);
        assertThat(state.getMarks().get(0)).containsEntry("NEXT", 1);
        assertThat(result.dartsThrown()).isEqualTo(3);
        assertThat(result.hits().get(2).getTotal()).isEqualTo(1);
    }

    @Test
    void bullDoesNotCountBeforeNumbersAreCompleted() {
        var result = AroundClockRules.play(AroundClockRules.initial(1), "25 2 25 1 1 1");
        assertThat(result.state().getMarks().get(0)).containsEntry("NEXT", 2);
        assertThat(result.winner()).isNull();
        assertThat(result.bulls()).isEqualTo(2);
    }

    @Test
    void doublesAndTreblesNearTwentyNeverWinWithoutASeparateBull() {
        for (int number : List.of(18, 19, 20)) {
            var state = AroundClockRules.initial(1);
            state.getMarks().get(0).put("NEXT", number);
            var result = AroundClockRules.play(state, number + " 3");
            assertThat(result.state().getMarks().get(0)).containsEntry("NEXT", 21);
            assertThat(result.winner()).isNull();
        }
        assertThat(AroundClockRules.advanceTarget(19, 19, 2)).isEqualTo(21);
        assertThat(AroundClockRules.advanceTarget(20, 20, 2)).isEqualTo(21);
    }

    @Test
    void eitherBullFinishesAndDartsAfterTheWinAreIgnored() {
        for (int multiplier : List.of(1, 2)) {
            var state = AroundClockRules.initial(1);
            state.getMarks().get(0).put("NEXT", 18);
            var result = AroundClockRules.play(state, "18 3 25 " + multiplier + " 20 3");
            assertThat(result.winner()).isZero();
            assertThat(result.dartsThrown()).isEqualTo(2);
            assertThat(result.state().getMarks().get(0)).containsEntry("NEXT", 0);
            assertThat(result.hits()).doesNotContainKey(20);
        }
    }

    @Test
    void invalidDartsIncludingThoseAfterAWinRejectTheWholeTurn() {
        var state = AroundClockRules.initial(1);
        state.getMarks().get(0).put("NEXT", 21);
        for (String score : List.of("25 3", "21 1", "0 2", "1 4", "25 1 25 3",
                "1 1 2 1 3 1 4 1", "1")) {
            assertThatThrownBy(() -> AroundClockRules.play(state, score))
                    .isInstanceOf(InvalidScoreException.class);
        }
        assertThat(state.getMarks().get(0)).containsEntry("NEXT", 21);
    }

    @Test
    void emptyAndMissedTurnsUseThreeDartsAndAdvanceToTheNextPlayer() {
        var result = AroundClockRules.play(AroundClockRules.initial(2), "");
        assertThat(result.dartsThrown()).isEqualTo(3);
        assertThat(result.state().getCurrentPosition()).isEqualTo(1);
        assertThat(result.hits()).isEmpty();
        assertThat(result.state().getMarks().get(0)).containsEntry("NEXT", 1);
    }

    @Test
    void redemptionMinimumRequiresOneSeparateBullDart() {
        for (int next = 1; next <= 21; next++) {
            int numbersLeft = 21 - next;
            int expected = (int) Math.ceil(numbersLeft / 3.0) + 1;
            assertThat(AroundClockRules.minimumDarts(Map.of("NEXT", next))).isEqualTo(expected);
        }
        assertThat(AroundClockRules.minimumDarts(Map.of("NEXT", 0))).isZero();
        assertThat(AroundClockRules.minimumDarts(Map.of("NEXT", 14))).isEqualTo(4);
        assertThat(AroundClockRules.minimumDarts(Map.of("NEXT", 15))).isEqualTo(3);
    }

    @Test
    void redemptionOnlyIncludesLaterPlayersAbleToFinishInThreeDarts() {
        var state = AroundClockRules.initial(4);
        state.setCurrentPosition(1);
        state.getMarks().get(0).put("NEXT", 21); // Earlier player gets no extra turn.
        state.getMarks().get(1).put("NEXT", 21);
        state.getMarks().get(2).put("NEXT", 14); // Needs four darts, so skipped.
        state.getMarks().get(3).put("NEXT", 15);
        var result = AroundClockRules.play(state, "25 1");
        assertThat(result.state().getPhase()).isEqualTo("REDEMPTION");
        assertThat(result.state().getRedemption()).containsExactly(3);
        result = AroundClockRules.play(result.state(), "15 3 18 3 25 2");
        assertThat(result.state().getPhase()).isEqualTo("BULL_OFF");
        assertThat(result.state().getBullCandidates()).containsExactly(1, 3);
    }

    @Test
    void failedRedemptionAwardsTheFirstFinisherAndBullTiesAutomaticallyRethrow() {
        var state = AroundClockRules.initial(2);
        state.getMarks().values().forEach(progress -> progress.put("NEXT", 21));
        var redemption = AroundClockRules.play(state, "25 1").state();
        assertThat(AroundClockRules.play(redemption, "0 1").winner()).isZero();
        var tied = AroundClockRules.play(redemption, "25 2").state();
        tied = AroundClockRules.play(tied, "25 2").state();
        tied = AroundClockRules.play(tied, "25 2").state();
        assertThat(tied.getPhase()).isEqualTo("BULL_OFF");
        assertThat(tied.getBullAttempt()).isEqualTo(2);
        tied = AroundClockRules.play(tied, "0 1").state();
        tied = AroundClockRules.play(tied, "0 1").state();
        assertThat(tied.getPhase()).isEqualTo("BULL_DECISION");
        assertThat(EqualRoundRules.decide(tied, 1).winner()).isEqualTo(1);
    }
}
