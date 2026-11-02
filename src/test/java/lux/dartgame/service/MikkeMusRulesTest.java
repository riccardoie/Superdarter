package lux.dartgame.service;

import java.util.List;
import java.util.Map;
import lux.dartgame.exception.GameConflictException;
import lux.dartgame.exception.InvalidScoreException;
import lux.dartgame.model.MikkeMusState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MikkeMusRulesTest {
    @Test
    void doubleAndTrebleAreAllocatedToExactlyOneCategory() {
        var state = MikkeMusRules.initial(1);
        var result = MikkeMusRules.play(state, "20 2 19 3 18 3",
                List.of("DOUBLE", "NUMBER", "TREBLE"), false);
        assertThat(result.state().getMarks().get(0)).containsEntry("D", 1)
                .containsEntry("20", 0).containsEntry("19", 3).containsEntry("18", 0)
                .containsEntry("T", 1);
        assertThat(state.getMarks().get(0)).allSatisfy((target, marks) -> assertThat(marks).isZero());
        result = MikkeMusRules.play(state, "20 2", List.of("NUMBER"), false);
        assertThat(result.state().getMarks().get(0)).containsEntry("20", 2).containsEntry("D", 0);
    }

    @Test
    void forcedNumbersAndCategoriesRequireTheCurrentNumber() {
        var state = MikkeMusRules.initial(1);
        var result = MikkeMusRules.play(state, "19 3 19 2 20 2",
                List.of("NUMBER", "DOUBLE", "DOUBLE"), true);
        assertThat(result.state().getMarks().get(0)).containsEntry("19", 0)
                .containsEntry("D", 1).containsEntry("20", 0);
        result = MikkeMusRules.play(state, "20 3 19 3 18 3", null, true);
        assertThat(result.state().getMarks().get(0)).containsEntry("20", 3)
                .containsEntry("19", 3).containsEntry("18", 3).containsEntry("17", 0);
        assertThat(result.dartsThrown()).isEqualTo(3);
    }

    @Test
    void forcedBullUnlocksOnlyAfterNumbersAndBothCategoriesClose() {
        var state = MikkeMusRules.initial(1);
        var result = MikkeMusRules.play(state, "25 2", null, true);
        assertThat(result.state().getMarks().get(0)).containsEntry("B", 0);
        MikkeMusRules.NUMBERS.forEach(key -> state.getMarks().get(0).put(key, 3));
        result = MikkeMusRules.play(state, "25 2", null, true);
        assertThat(result.state().getMarks().get(0)).containsEntry("B", 0);
        result = MikkeMusRules.play(state, "1 2 5 3 25 2",
                List.of("DOUBLE", "TREBLE", "BULL"), true);
        assertThat(result.state().getMarks().get(0)).containsEntry("D", 1)
                .containsEntry("T", 1).containsEntry("B", 0);
        state.getMarks().get(0).put("D", 3);
        state.getMarks().get(0).put("T", 3);
        result = MikkeMusRules.play(state, "25 2 25 1 20 3", null, true);
        assertThat(result.winner()).isEqualTo(0);
        assertThat(result.dartsThrown()).isEqualTo(2);
        assertThat(result.hits()).doesNotContainKey(20);
    }

    @Test
    void relaxedBullAndTargetsCanCloseInAnyOrderWithMarksCappedAtThree() {
        var result = MikkeMusRules.play(MikkeMusRules.initial(1), "14 3 25 2 25 2", null, false);
        assertThat(result.state().getMarks().get(0)).containsEntry("14", 3).containsEntry("B", 3);
        assertThat(result.winner()).isNull();
        assertThat(MikkeMusRules.minimumDarts(MikkeMusRules.initial(1).getMarks().get(0)))
                .isEqualTo(15);
    }

    @Test
    void redemptionOnlyIncludesLaterPlayersWhoCanFinishInThreeDarts() {
        var state = MikkeMusRules.initial(4);
        closeExceptBull(state, 0, 2);
        closeExceptBull(state, 1, 2);
        closeExceptBull(state, 3, 0);
        state.getMarks().get(3).put("D", 1); // Four darts needed, so no redemption.
        var result = MikkeMusRules.play(state, "25 1", null, false);
        assertThat(result.winner()).isNull();
        assertThat(result.state().getPhase()).isEqualTo("REDEMPTION");
        assertThat(result.state().getRedemption()).containsExactly(1);
        assertThat(result.state().getCurrentPosition()).isEqualTo(1);
        result = MikkeMusRules.play(result.state(), "0 1 0 1 0 1", null, false);
        assertThat(result.winner()).isEqualTo(0);
    }

    @Test
    void earlierPlayersDoNotGetAnExtraTurnAndIneligiblePlayersAreSkipped() {
        var state = MikkeMusRules.initial(3);
        closeExceptBull(state, 0, 2);
        closeExceptBull(state, 1, 2);
        state.setCurrentPosition(1);
        var result = MikkeMusRules.play(state, "25 1", null, true);
        assertThat(result.winner()).isEqualTo(1);
        assertThat(result.state().getFinishers()).containsExactly(1);
    }

    @Test
    void tiedFinishersThrowForBullAndDistinctRegionsDetermineTheWinner() {
        var tied = tie();
        assertThat(tied.getBullCandidates()).containsExactly(0, 1);
        var first = MikkeMusRules.play(tied, "25 1", null, false);
        assertThat(first.winner()).isNull();
        assertThat(first.state().getCurrentPosition()).isEqualTo(1);
        var second = MikkeMusRules.play(first.state(), "25 2", null, false);
        assertThat(second.winner()).isEqualTo(1);
        assertThat(second.state().getBullHistory()).hasSize(2);
        assertThat(second.dartsThrown()).isEqualTo(1);
    }

    @Test
    void outsideBullRequiresAnActualDistanceDecisionOrAnotherThrow() {
        var state = MikkeMusRules.play(tie(), "0 1", null, false).state();
        state = MikkeMusRules.play(state, "0 1", null, false).state();
        assertThat(state.getPhase()).isEqualTo("BULL_DECISION");
        assertThat(state.getCurrentPosition()).isEqualTo(-1);
        assertThat(MikkeMusRules.decide(state, 1).winner()).isEqualTo(1);
        var replay = MikkeMusRules.decide(state, null);
        assertThat(replay.winner()).isNull();
        assertThat(replay.dartsThrown()).isZero();
        assertThat(replay.state().getPhase()).isEqualTo("BULL_OFF");
        assertThat(replay.state().getBullHistory()).hasSize(2);
        assertThat(replay.state().getBullAttempt()).isEqualTo(2);
        assertThat(replay.state().getBullThrows()).isEmpty();
        assertThat(state.getPhase()).isEqualTo("BULL_DECISION");
        var decision = state;
        assertThatThrownBy(() -> MikkeMusRules.decide(decision, 2))
                .isInstanceOf(GameConflictException.class);
        assertThatThrownBy(() -> MikkeMusRules.play(decision, "25 2", null, false))
                .isInstanceOf(GameConflictException.class);
    }

    @Test
    void tiedOuterBullsAndBullseyesAutomaticallyRethrow() {
        for (int region : List.of(1, 2)) {
            var first = MikkeMusRules.play(tie(), "25 " + region, null, false).state();
            var result = MikkeMusRules.play(first, "25 " + region, null, false);
            assertThat(result.winner()).isNull();
            assertThat(result.state().getPhase()).isEqualTo("BULL_OFF");
            assertThat(result.state().getBullAttempt()).isEqualTo(2);
            assertThat(result.state().getBullCandidates()).containsExactly(0, 1);
            assertThat(result.state().getBullThrows()).isEmpty();
            assertThat(result.state().getBullHistory()).hasSize(2);
            assertThat(result.state().getCurrentPosition()).isZero();
            assertThat(result.dartsThrown()).isEqualTo(1);
            assertThat(result.hits().get(25).getTotal()).isEqualTo(1);
            assertThat(first.getBullAttempt()).isEqualTo(1);
            var next = MikkeMusRules.play(result.state(), "0 1", null, false).state();
            assertThat(MikkeMusRules.play(next, "25 1", null, false).winner()).isEqualTo(1);
        }
    }

    @Test
    void automaticRethrowsKeepOnlyTiedBestBullRegions() {
        var state = MikkeMusRules.initial(3);
        state.setPhase("BULL_OFF");
        state.setBullCandidates(new java.util.ArrayList<>(List.of(0, 1, 2)));
        state = MikkeMusRules.play(state, "25 2", null, false).state();
        state = MikkeMusRules.play(state, "25 1", null, false).state();
        state = MikkeMusRules.play(state, "25 2", null, false).state();
        assertThat(state.getBullCandidates()).containsExactly(0, 2);
        assertThat(state.getBullAttempt()).isEqualTo(2);
        state = MikkeMusRules.play(state, "25 1", null, false).state();
        state = MikkeMusRules.play(state, "25 1", null, false).state();
        assertThat(state.getBullCandidates()).containsExactly(0, 2);
        assertThat(state.getBullAttempt()).isEqualTo(3);
        assertThat(state.getBullHistory()).hasSize(5);
    }

    @Test
    void invalidAllocationsAndExtraBullOffDartsAreRejectedAtomically() {
        var state = MikkeMusRules.initial(1);
        assertThatThrownBy(() -> MikkeMusRules.play(state, "25 2", List.of("DOUBLE"), false))
                .isInstanceOf(InvalidScoreException.class);
        assertThatThrownBy(() -> MikkeMusRules.play(state, "20 1", List.of("DOUBLE"), false))
                .isInstanceOf(InvalidScoreException.class);
        assertThatThrownBy(() -> MikkeMusRules.play(state, "20 2", List.of("TREBLE"), false))
                .isInstanceOf(InvalidScoreException.class);
        assertThatThrownBy(() -> MikkeMusRules.play(state, "20 3", List.of("BULL"), false))
                .isInstanceOf(InvalidScoreException.class);
        assertThatThrownBy(() -> MikkeMusRules.play(state, "20 3", List.of(), false))
                .isInstanceOf(InvalidScoreException.class);
        assertThatThrownBy(() -> MikkeMusRules.play(tie(), "25 1 25 2", null, false))
                .isInstanceOf(InvalidScoreException.class);
        assertThatThrownBy(() -> MikkeMusRules.play(tie(), "20 1", null, false))
                .isInstanceOf(InvalidScoreException.class);
        assertThat(state.getMarks().get(0)).containsEntry("20", 0);
    }

    @Test
    void winningDartsStopProcessingButEntireInputStillRequiresValidation() {
        var state = MikkeMusRules.initial(1);
        closeExceptBull(state, 0, 2);
        var result = MikkeMusRules.play(state, "25 1 20 3", null, false);
        assertThat(result.winner()).isEqualTo(0);
        assertThat(result.hits()).containsKey(25).doesNotContainKey(20);
        assertThatThrownBy(() -> MikkeMusRules.play(state, "25 1 25 3", null, false))
                .isInstanceOf(InvalidScoreException.class);
    }

    private MikkeMusState tie() {
        var state = MikkeMusRules.initial(2);
        closeExceptBull(state, 0, 2);
        closeExceptBull(state, 1, 2);
        state = MikkeMusRules.play(state, "25 1", null, false).state();
        return MikkeMusRules.play(state, "25 1", null, false).state();
    }

    private void closeExceptBull(final MikkeMusState state, final int position, final int bull) {
        state.getMarks().get(position).replaceAll((target, marks) -> 3);
        state.getMarks().get(position).put("B", bull);
    }
}
