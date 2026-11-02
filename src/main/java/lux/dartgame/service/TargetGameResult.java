package lux.dartgame.service;

import java.util.Map;
import lux.dartgame.model.BoardHitCounts;
import lux.dartgame.model.TargetGameState;

public record TargetGameResult(TargetGameState state, Integer winner, int dartsThrown,
                               int triples, int bulls, Map<Integer, BoardHitCounts> hits) {
}
