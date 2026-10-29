package lux.dartgame.dto;

import java.time.Instant;
import lux.dartgame.model.FriendRequestStatus;

public record FriendRequestResponse(long requestId, String sender, String recipient,
                                    FriendRequestStatus status, Instant createdAt,
                                    Instant acceptedAt) {
}
