package lux.dartgame.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lux.dartgame.constants.Constants;
import lux.dartgame.exception.GameConflictException;
import lux.dartgame.model.BoardHitCounts;
import lux.dartgame.model.TargetGameState;

/** NEXT is 1-20, 21 for bull, or 0 after a separate finishing bull dart. */
public final class AroundClockRules {
    public static final int BULL_TARGET = Constants.MAX_DART_NUMBER + 1;
    public static final String NEXT = "NEXT";

    private AroundClockRules() {
    }

    public static TargetGameState initial(final int players) {
        var state = new TargetGameState();
        for (int position = 0; position < players; position++) {
            state.getMarks().put(position, new LinkedHashMap<>(Map.of(NEXT, 1)));
        }
        return state;
    }

    public static int minimumDarts(final Map<String, Integer> progress) {
        int next = progress.get(NEXT);
        return next == 0 ? 0 : (BULL_TARGET - next + Constants.MAX_MULTIPLIER - 1)
                / Constants.MAX_MULTIPLIER + 1;
    }

    public static int advanceTarget(final int target, final int number, final int multiplier) {
        if (target == BULL_TARGET) {
            return number == Constants.BULLSEYE_VALUE ? 0 : target;
        }
        return target > 0 && number == target ? Math.min(BULL_TARGET, target + multiplier) : target;
    }

    public static TargetGameResult play(final TargetGameState before, final String score) {
        if (!List.of("PLAY", "REDEMPTION", "BULL_OFF").contains(before.getPhase())) {
            throw new GameConflictException("Resolve the bull-off before entering another turn");
        }
        int[] darts = CountdownRules.parse(score);
        if ("BULL_OFF".equals(before.getPhase())) {
            return EqualRoundRules.bullThrow(before, darts);
        }
        var state = before.copy();
        var progress = state.getMarks().get(state.getCurrentPosition());
        int target = progress.get(NEXT);
        int thrown = 0;
        int triples = 0;
        int bulls = 0;
        Map<Integer, BoardHitCounts> hits = new HashMap<>();
        for (int i = 0; i < darts.length; i += 2) {
            int number = darts[i];
            int multiplier = darts[i + 1];
            thrown++;
            if (number > 0) {
                hits.computeIfAbsent(number, ignored -> new BoardHitCounts()).record(multiplier);
            }
            if (number == Constants.MAX_DART_NUMBER && multiplier == Constants.MAX_MULTIPLIER) {
                triples++;
            }
            if (number == Constants.BULLSEYE_VALUE) {
                bulls++;
            }
            target = advanceTarget(target, number, multiplier);
            if (target == 0) {
                break;
            }
        }
        progress.put(NEXT, target);
        boolean finished = target == 0;
        if (!finished) {
            thrown = Constants.MAX_DARTS_PER_TURN;
        }
        Integer winner = EqualRoundRules.advance(state, finished, AroundClockRules::minimumDarts);
        return new TargetGameResult(state, winner, thrown, triples, bulls, Map.copyOf(hits));
    }
}
