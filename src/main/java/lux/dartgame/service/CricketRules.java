package lux.dartgame.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lux.dartgame.constants.Constants;
import lux.dartgame.model.BoardHitCounts;

/** Standard scoring cricket: close targets and be tied for or ahead on points. */
public final class CricketRules {
    public static final List<Integer> TARGETS = List.of(20, 19, 18, 17, 16, 15, 25);

    private CricketRules() {
    }

    public record Player(int points, Map<Integer, Integer> marks) {
    }

    public record Result(int points, Map<Integer, Integer> marks, int scored,
                         boolean won, int dartsThrown, int triples, int bullseyes,
                         Map<Integer, BoardHitCounts> boardHits) {
    }

    public static Result evaluate(final Player player, final List<Player> opponents,
                                  final String score) {
        int[] darts = CountdownRules.parse(score);
        Map<Integer, Integer> marks = new HashMap<>(player.marks());
        Map<Integer, BoardHitCounts> hits = new HashMap<>();
        int points = player.points();
        int thrown = 0;
        int triples = 0;
        int bulls = 0;
        boolean won = false;
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
            if (TARGETS.contains(number)) {
                int before = marks.getOrDefault(number, 0);
                int extra = Math.max(0, before + multiplier - Constants.MAX_MULTIPLIER);
                marks.put(number, Math.min(Constants.MAX_MULTIPLIER, before + multiplier));
                if (opponents.stream().anyMatch(other -> other.marks().getOrDefault(number, 0)
                        < Constants.MAX_MULTIPLIER)) {
                    points += extra * number;
                }
            }
            int currentPoints = points;
            won = TARGETS.stream().allMatch(target -> marks.getOrDefault(target, 0)
                    == Constants.MAX_MULTIPLIER)
                    && opponents.stream().allMatch(other -> currentPoints >= other.points());
            if (won) {
                break;
            }
        }
        return new Result(points, Map.copyOf(marks), points - player.points(), won,
                won ? thrown : Constants.MAX_DARTS_PER_TURN, triples, bulls, Map.copyOf(hits));
    }
}
