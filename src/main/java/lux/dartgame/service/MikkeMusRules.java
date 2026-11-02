package lux.dartgame.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lux.dartgame.constants.Constants;
import lux.dartgame.exception.GameConflictException;
import lux.dartgame.exception.InvalidScoreException;
import lux.dartgame.model.BoardHitCounts;
import lux.dartgame.model.TargetGameState;

/** Mark-only Mickey Mouse, with an equal-round redemption and a physical bull-off. */
public final class MikkeMusRules {
    public static final List<String> NUMBERS = List.of("20", "19", "18", "17", "16", "15", "14");
    public static final List<String> TARGETS = List.of(
            "20", "19", "18", "17", "16", "15", "14", "D", "T", "B");
    private static final int CLOSED = 3;

    private MikkeMusRules() {
    }

    public static boolean supports(final String type) {
        return Constants.GAMETYPE_MIKKE_FORCED.equals(type)
                || Constants.GAMETYPE_MIKKE_RELAXED.equals(type);
    }

    public static TargetGameState initial(final int players) {
        var state = new TargetGameState();
        for (int position = 0; position < players; position++) {
            Map<String, Integer> marks = new LinkedHashMap<>();
            TARGETS.forEach(target -> marks.put(target, 0));
            state.getMarks().put(position, marks);
        }
        return state;
    }

    public static int minimumDarts(final Map<String, Integer> marks) {
        int numbers = (int) NUMBERS.stream().filter(key -> marks.get(key) < CLOSED).count();
        return numbers + CLOSED - marks.get("D") + CLOSED - marks.get("T")
                + (CLOSED - marks.get("B") + 1) / 2;
    }

    public static TargetGameResult play(final TargetGameState before, final String score,
                              final List<String> allocations, final boolean forced) {
        if (!List.of("PLAY", "REDEMPTION", "BULL_OFF").contains(before.getPhase())) {
            throw new GameConflictException("Resolve the bull-off before entering another turn");
        }
        int[] darts = CountdownRules.parse(score);
        if ("BULL_OFF".equals(before.getPhase())) {
            return EqualRoundRules.bullThrow(before, darts);
        }
        if (allocations != null && allocations.size() != darts.length / 2) {
            throw new InvalidScoreException();
        }
        // Validate the entire submission, including any input after a winning dart.
        for (int i = 0; i < darts.length / 2; i++) {
            allocation(darts[i * 2], darts[i * 2 + 1], allocations, i);
        }
        var state = before.copy();
        var marks = state.getMarks().get(state.getCurrentPosition());
        Map<Integer, BoardHitCounts> hits = new HashMap<>();
        int thrown = 0;
        int triples = 0;
        int bulls = 0;
        for (int i = 0; i < darts.length / 2; i++) {
            int number = darts[i * 2];
            int multiplier = darts[i * 2 + 1];
            String use = allocation(number, multiplier, allocations, i);
            record(hits, number, multiplier);
            thrown++;
            if (number == Constants.MAX_DART_NUMBER && multiplier == CLOSED) {
                triples++;
            }
            if (number == Constants.BULLSEYE_VALUE) {
                bulls++;
            }
            apply(marks, number, multiplier, use, forced);
            if (minimumDarts(marks) == 0) {
                break;
            }
        }
        boolean finished = minimumDarts(marks) == 0;
        if (!finished) {
            thrown = Constants.MAX_DARTS_PER_TURN;
        }
        Integer winner = EqualRoundRules.advance(state, finished, MikkeMusRules::minimumDarts);
        return new TargetGameResult(state, winner, thrown, triples, bulls, Map.copyOf(hits));
    }

    private static String allocation(final int number, final int multiplier,
                                     final List<String> choices, final int index) {
        String use = choices == null ? (number == Constants.BULLSEYE_VALUE ? "BULL" : "NUMBER")
                : choices.get(index);
        boolean valid = "NUMBER".equals(use) || "BULL".equals(use)
                && number == Constants.BULLSEYE_VALUE
                || "DOUBLE".equals(use) && multiplier == 2 && number > 0
                && number != Constants.BULLSEYE_VALUE
                || "TREBLE".equals(use) && multiplier == CLOSED && number > 0;
        if (!valid) {
            throw new InvalidScoreException();
        }
        return use;
    }

    private static void apply(final Map<String, Integer> marks, final int number,
                              final int multiplier, final String use, final boolean forced) {
        String required = NUMBERS.stream().filter(key -> marks.get(key) < CLOSED)
                .findFirst().orElse(null);
        boolean exact = required == null || required.equals(Integer.toString(number));
        String key = null;
        int amount = 1;
        if ("NUMBER".equals(use) && NUMBERS.contains(Integer.toString(number))
                && (!forced || exact)) {
            key = Integer.toString(number);
            amount = multiplier;
        } else if (("DOUBLE".equals(use) || "TREBLE".equals(use))
                && (!forced || exact)) {
            key = "DOUBLE".equals(use) ? "D" : "T";
        } else if ("BULL".equals(use) && (!forced || required == null
                && marks.get("D") == CLOSED && marks.get("T") == CLOSED)) {
            key = "B";
            amount = multiplier;
        }
        if (key != null) {
            marks.put(key, Math.min(CLOSED, marks.get(key) + amount));
        }
    }

    public static TargetGameResult decide(final TargetGameState before, final Integer winner) {
        return EqualRoundRules.decide(before, winner);
    }

    private static void record(final Map<Integer, BoardHitCounts> hits, final int number,
                               final int multiplier) {
        if (number > 0) {
            hits.computeIfAbsent(number, ignored -> new BoardHitCounts()).record(multiplier);
        }
    }
}
