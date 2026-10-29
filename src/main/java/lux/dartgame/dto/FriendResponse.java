package lux.dartgame.dto;

import java.time.Instant;

public record FriendResponse(String username, Instant friendsSince) {
}
