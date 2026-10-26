package lux.dartgame.service;

import lux.dartgame.constants.Constants;
import lux.dartgame.exception.InvalidScoreException;

/** Stateless double-out rules, independent of match persistence and progression. */
public final class CountdownRules {
    private CountdownRules() {
    }

    public record Result(int remaining, int scored, int triples, int bullseyes,
                         boolean won, boolean bust) {
    }

    public static Result evaluate(final int startingPoints, final String score) {
        int[] darts = parse(score);
        int remaining = startingPoints;
        int triples = 0;
        int bullseyes = 0;
        boolean won = false;
        boolean bust = false;
        for (int i = 0; i < darts.length; i += 2) {
            int number = darts[i];
            int multiplier = darts[i + 1];
            remaining -= number * multiplier;
            if (number == Constants.MAX_DART_NUMBER && multiplier == Constants.MAX_MULTIPLIER) {
                triples++;
            }
            if (number == Constants.BULLSEYE_VALUE) {
                bullseyes++;
            }
            if (remaining < 0 || remaining == 1 || (remaining == 0 && multiplier != 2)) {
                bust = true;
                break;
            }
            if (remaining == 0) {
                won = true;
                break;
            }
        }
        return new Result(bust ? startingPoints : remaining,
                bust ? 0 : startingPoints - remaining, triples, bullseyes, won, bust);
    }

    private static int[] parse(final String score) {
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
