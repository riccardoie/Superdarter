package lux.dartgame.controller;

import java.security.Principal;
import java.util.List;
import jakarta.validation.Valid;
import lux.dartgame.dto.FriendInvitationsResponse;
import lux.dartgame.dto.FriendRequestResponse;
import lux.dartgame.dto.FriendResponse;
import lux.dartgame.dto.SendFriendRequest;
import lux.dartgame.service.FriendService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/friends")
public class FriendsController {
    private final FriendService friends;

    public FriendsController(final FriendService friendsParam) {
        friends = friendsParam;
    }

    @GetMapping
    public List<FriendResponse> list(final Principal principal) {
        return friends.friends(principal.getName());
    }

    @GetMapping("requests")
    public FriendInvitationsResponse invitations(final Principal principal) {
        return friends.invitations(principal.getName());
    }

    @PostMapping("requests")
    @ResponseStatus(HttpStatus.CREATED)
    public FriendRequestResponse send(final @RequestBody @Valid SendFriendRequest request,
                                      final Principal principal) {
        return friends.send(principal.getName(), request.username());
    }

    @PostMapping("requests/{id}/accept")
    public FriendRequestResponse accept(final @PathVariable long id, final Principal principal) {
        return friends.accept(id, principal.getName());
    }

    @DeleteMapping("requests/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(final @PathVariable long id, final Principal principal) {
        friends.dismiss(id, principal.getName());
    }
}
