package lux.dartgame.dto;

public record PlayerStatResponse(String username,
                                 float avgPoints,
                                 int wonMatches,
                                 int playedMatches,
                                 int triple20s,
                                 int bullseyes,
                                 int highestScore,
                                 int highestCheckout,
                                 float avgCheckoutAccuracy,
                                 int wonLegs,
                                 int playedLegs) {
}
