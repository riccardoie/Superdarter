package lux.dartgame.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lux.dartgame.constants.Constants;
import lux.dartgame.exception.GameConflictException;
import lux.dartgame.exception.InvalidScoreException;
import lux.dartgame.model.BoardHitCounts;
import lux.dartgame.model.MikkeMusState;

/** Mark-only Mickey Mouse, with an equal-round redemption and a physical bull-off. */
public final class MikkeMusRules {
    public static final List<String> NUMBERS = List.of("20", "19", "18", "17", "16", "15", "14");
    public static final List<String> TARGETS = List.of(
            "20", "19", "18", "17", "16", "15", "14", "D", "T", "B");
    private static final int CLOSED = 3;

    private MikkeMusRules() {
    }

    public record Result(MikkeMusState state, Integer winner, int dartsThrown,
                         int triples, int bulls, Map<Integer, BoardHitCounts> hits) {
    }

    public static boolean supports(final String type) {
        return Constants.GAMETYPE_MIKKE_FORCED.equals(type)
                || Constants.GAMETYPE_MIKKE_RELAXED.equals(type);
    }

    public static MikkeMusState initial(final int players) {
        var state = new MikkeMusState();
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

    public static Result play(final MikkeMusState before, final String score,
                              final List<String> allocations, final boolean forced) {
        if (!List.of("PLAY", "REDEMPTION", "BULL_OFF").contains(before.getPhase())) {
            throw new GameConflictException("Resolve the bull-off before entering another turn");
        }
        int[] darts = CountdownRules.parse(score);
        if ("BULL_OFF".equals(before.getPhase())) {
            return bullThrow(before, darts);
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
        if (finished) {
            state.getFinishers().add(state.getCurrentPosition());
        }
        Integer winner = advance(state, finished);
        return new Result(state, winner, thrown, triples, bulls, Map.copyOf(hits));
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

    private static Integer advance(final MikkeMusState state, final boolean finished) {
        if ("PLAY".equals(state.getPhase())) {
            if (!finished) {
                state.setCurrentPosition((state.getCurrentPosition() + 1)
                        % state.getMarks().size());
                return null;
            }
            state.setPhase("REDEMPTION");
            for (int position = state.getCurrentPosition() + 1;
                    position < state.getMarks().size(); position++) {
                if (minimumDarts(state.getMarks().get(position)) <= Constants.MAX_DARTS_PER_TURN) {
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

    private static Result bullThrow(final MikkeMusState before, final int[] darts) {
        if (darts.length != 2 || darts[0] != 0 && darts[0] != Constants.BULLSEYE_VALUE) {
            throw new InvalidScoreException();
        }
        var state = before.copy();
        int position = state.getCurrentPosition();
        int region = darts[0] == 0 ? 0 : darts[1];
        state.getBullThrows().put(position, region);
        state.getBullHistory().add(new MikkeMusState.BullThrow(position, region,
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
        record(hits, darts[0], darts[1]);
        return new Result(state, winner, 1, 0, region > 0 ? 1 : 0, hits);
    }

    public static Result decide(final MikkeMusState before, final Integer winner) {
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
        return new Result(state, winner, 0, 0, 0, Map.of());
    }

    private static void restartBullOff(final MikkeMusState state) {
        state.setPhase("BULL_OFF");
        state.setBullThrows(new LinkedHashMap<>());
        state.setBullAttempt(state.getBullAttempt() + 1);
        state.setCurrentPosition(state.getBullCandidates().getFirst());
    }

    private static int finish(final MikkeMusState state, final int winner) {
        state.setPhase("FINISHED");
        state.setCurrentPosition(-1);
        return winner;
    }

    private static void record(final Map<Integer, BoardHitCounts> hits, final int number,
                               final int multiplier) {
        if (number > 0) {
            hits.computeIfAbsent(number, ignored -> new BoardHitCounts()).record(multiplier);
        }
    }
}
