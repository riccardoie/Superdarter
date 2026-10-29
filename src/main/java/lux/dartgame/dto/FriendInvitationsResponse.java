package lux.dartgame.dto;

import java.util.List;

public record FriendInvitationsResponse(List<FriendRequestResponse> incoming,
                                        List<FriendRequestResponse> outgoing) {
}
