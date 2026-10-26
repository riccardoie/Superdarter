package lux.dartgame.dto;

public record GameStatResponse(String userName,
                               int points,
                               int turns,
                               int bullseyes,
                               int triple20s,
                               int highestScore,
                               int highestCheckout,
                               float checkoutAccuracy,
                               int startingPoints) {
}
