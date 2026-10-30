package lux.dartgame.service;

import lux.dartgame.constants.Constants;
import lux.dartgame.exception.GameModeNotFoundException;
import lux.dartgame.exception.InvalidScoreException;
import java.util.HashMap;
import java.util.Map;
import lux.dartgame.model.BoardHitCounts;

/** Stateless countdown rules, independent of match persistence and progression. */
public final class CountdownRules {
    private CountdownRules() {
    }

    public static int startingPoints(final String gametype) {
        return switch (gametype) {
            case Constants.GAMETYPE_301, Constants.GAMETYPE_301_SINGLE_OUT -> Constants.TARGET_301;
            case Constants.GAMETYPE_501, Constants.GAMETYPE_501_SINGLE_OUT -> Constants.TARGET_501;
            case Constants.GAMETYPE_CRICKET -> 0;
            default -> throw new GameModeNotFoundException(gametype);
        };
    }

    public record Result(int remaining, int scored, int triples, int bullseyes,
                         boolean won, boolean bust, int dartsThrown,
                         int firstNinePoints, int firstNineDarts,
                         Map<Integer, BoardHitCounts> boardHits) {
    }

    public static Result evaluate(final int startingPoints, final String score) {
        return evaluate(startingPoints, score, Constants.FIRST_NINE_DARTS);
    }

    public static Result evaluate(final int startingPoints, final String score,
                                  final int firstNineDartsLeft) {
        return evaluate(startingPoints, score, firstNineDartsLeft, true);
    }

    public static Result evaluate(final int startingPoints, final String score,
                                  final int firstNineDartsLeft, final boolean doubleOut) {
        int[] darts = parse(score);
        int remaining = startingPoints;
        int triples = 0;
        int bullseyes = 0;
        boolean won = false;
        boolean bust = false;
        int thrown = 0;
        int firstPoints = 0;
        Map<Integer, BoardHitCounts> boardHits = new HashMap<>();
        for (int i = 0; i < darts.length; i += 2) {
            int number = darts[i];
            int multiplier = darts[i + 1];
            if (number > 0) {
                boardHits.computeIfAbsent(number, ignored -> new BoardHitCounts())
                        .record(multiplier);
            }
            thrown++;
            if (thrown <= firstNineDartsLeft) {
                firstPoints += number * multiplier;
            }
            remaining -= number * multiplier;
            if (number == Constants.MAX_DART_NUMBER && multiplier == Constants.MAX_MULTIPLIER) {
                triples++;
            }
            if (number == Constants.BULLSEYE_VALUE) {
                bullseyes++;
            }
            if (remaining < 0 || (doubleOut
                    && (remaining == 1 || (remaining == 0 && multiplier != 2)))) {
                bust = true;
                break;
            }
            if (remaining == 0) {
                won = true;
                break;
            }
        }
        // Every submitted turn uses three darts, including omitted misses and busts.
        // Only a successful checkout ends the turn with fewer darts.
        if (!won) {
            thrown = Constants.MAX_DARTS_PER_TURN;
        }
        return new Result(bust ? startingPoints : remaining,
                bust ? 0 : startingPoints - remaining, triples, bullseyes, won, bust,
                thrown, bust ? 0 : firstPoints, Math.min(thrown, firstNineDartsLeft),
                bust ? Map.of() : Map.copyOf(boardHits));
    }

    static int[] parse(final String score) {
        if (score == null) {
            throw new InvalidScoreException();
        }
        if (score.isBlank()) {
            return new int[0]; // An empty round means all darts missed.
        }
        String[] tokens = score.trim().split("\\s+");
        if (tokens.length % 2 != 0 || tokens.length / 2 > Constants.MAX_DARTS_PER_TURN) {
            throw new InvalidScoreException();
        }
        int[] results = new int[tokens.length];
        try {
            for (int i = 0; i < tokens.length; i++) {
                results[i] = Integer.parseInt(tokens[i]);
            }
        } catch (NumberFormatException e) {
            throw new InvalidScoreException();
        }
        for (int i = 0; i < results.length; i += 2) {
            int number = results[i];
            int multiplier = results[i + 1];
            boolean bull = number == Constants.BULLSEYE_VALUE;
            boolean regular = number >= 1 && number <= Constants.MAX_DART_NUMBER;
            boolean valid = (number == 0 && multiplier == 1) || (bull
                    ? multiplier == 1 || multiplier == 2
                    : regular && multiplier >= 1 && multiplier <= Constants.MAX_MULTIPLIER);
            if (!valid) {
                throw new InvalidScoreException();
            }
        }
        return results;
    }
}
