package lux.dartgame.dto;

import java.util.Map;

public record PlayerStatResponse(String username,
                                 float avgPoints,
                                 int wonMatches,
                                 int playedMatches,
                                 int triple20s,
                                 int bullseyes,
                                 int highestScore,
                                 int highestCheckout,
                                 int wonLegs,
                                 int playedLegs,
                                 Long dartsThrown,
                                 Map<Integer, BoardHitResponse> boardHits,
                                 Float highestLegAverage,
                                 Float highestGameAverage,
                                 Float highestFirstNineAverage) {
}
