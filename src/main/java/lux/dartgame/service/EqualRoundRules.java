package lux.dartgame.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToIntFunction;
import lux.dartgame.constants.Constants;
import lux.dartgame.exception.GameConflictException;
import lux.dartgame.exception.InvalidScoreException;
import lux.dartgame.model.BoardHitCounts;
import lux.dartgame.model.TargetGameState;

/** Shared same-round redemption and bull-off progression for target games. */
public final class EqualRoundRules {
    private EqualRoundRules() {
    }

    public static Integer advance(final TargetGameState state, final boolean finished,
                                   final ToIntFunction<Map<String, Integer>> minimumDarts) {
        if (finished) {
            state.getFinishers().add(state.getCurrentPosition());
        }
        if ("PLAY".equals(state.getPhase())) {
            if (!finished) {
                state.setCurrentPosition((state.getCurrentPosition() + 1)
                        % state.getMarks().size());
                return null;
            }
            state.setPhase("REDEMPTION");
            for (int position = state.getCurrentPosition() + 1;
                    position < state.getMarks().size(); position++) {
                if (minimumDarts.applyAsInt(state.getMarks().get(position))
                        <= Constants.MAX_DARTS_PER_TURN) {
                    state.getRedemption().add(position);
                }
            }
        } else {
            state.getRedemption().removeFirst();
        }
        if (!state.getRedemption().isEmpty()) {
            state.setCurrentPosition(state.getRedemption().getFirst());
            return null;
        }
        if (state.getFinishers().size() == 1) {
            return finish(state, state.getFinishers().getFirst());
        }
        state.setPhase("BULL_OFF");
        state.setBullCandidates(new ArrayList<>(state.getFinishers()));
        state.setCurrentPosition(state.getBullCandidates().getFirst());
        return null;
    }

    public static TargetGameResult bullThrow(final TargetGameState before, final int[] darts) {
        if (darts.length != 2 || darts[0] != 0 && darts[0] != Constants.BULLSEYE_VALUE) {
            throw new InvalidScoreException();
        }
        var state = before.copy();
        int position = state.getCurrentPosition();
        int region = darts[0] == 0 ? 0 : darts[1];
        state.getBullThrows().put(position, region);
        state.getBullHistory().add(new TargetGameState.BullThrow(position, region,
                state.getBullAttempt()));
        var remaining = state.getBullCandidates().stream()
                .filter(candidate -> !state.getBullThrows().containsKey(candidate)).toList();
        Integer winner = null;
        if (!remaining.isEmpty()) {
            state.setCurrentPosition(remaining.getFirst());
        } else {
            int best = state.getBullThrows().values().stream().mapToInt(Integer::intValue).max()
                    .orElseThrow();
            state.setBullCandidates(state.getBullCandidates().stream()
                    .filter(candidate -> state.getBullThrows().get(candidate) == best)
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
            if (state.getBullCandidates().size() == 1) {
                winner = finish(state, state.getBullCandidates().getFirst());
            } else if (best > 0) {
                restartBullOff(state);
            } else {
                state.setPhase("BULL_DECISION");
                state.setCurrentPosition(-1);
            }
        }
        Map<Integer, BoardHitCounts> hits = new HashMap<>();
        if (region > 0) {
            hits.computeIfAbsent(Constants.BULLSEYE_VALUE, ignored -> new BoardHitCounts())
                    .record(region);
        }
        return new TargetGameResult(state, winner, 1, 0, region > 0 ? 1 : 0, hits);
    }

    public static TargetGameResult decide(final TargetGameState before, final Integer winner) {
        if (!"BULL_DECISION".equals(before.getPhase())) {
            throw new GameConflictException("There is no bull-off distance decision pending");
        }
        var state = before.copy();
        if (winner == null) {
            restartBullOff(state);
        } else if (state.getBullCandidates().contains(winner)) {
            finish(state, winner);
        } else {
            throw new GameConflictException("Choose one of the tied bull-off players");
        }
        return new TargetGameResult(state, winner, 0, 0, 0, Map.of());
    }

    private static void restartBullOff(final TargetGameState state) {
        state.setPhase("BULL_OFF");
        state.setBullThrows(new LinkedHashMap<>());
        state.setBullAttempt(state.getBullAttempt() + 1);
        state.setCurrentPosition(state.getBullCandidates().getFirst());
    }

    private static int finish(final TargetGameState state, final int winner) {
        state.setPhase("FINISHED");
        state.setCurrentPosition(-1);
        return winner;
    }
}
