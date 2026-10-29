package lux.dartgame.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import lux.dartgame.dto.FriendInvitationsResponse;
import lux.dartgame.dto.FriendRequestResponse;
import lux.dartgame.dto.FriendResponse;
import lux.dartgame.exception.AccessDeniedException;
import lux.dartgame.exception.FriendConflictException;
import lux.dartgame.exception.FriendRequestNotFoundException;
import lux.dartgame.exception.UsernameNotFoundException;
import lux.dartgame.model.FriendRequest;
import lux.dartgame.model.FriendRequestStatus;
import lux.dartgame.model.User;
import lux.dartgame.repository.FriendRequestRepository;
import lux.dartgame.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FriendService {
    private final FriendRequestRepository requests;
    private final UserRepository users;

    public FriendService(final FriendRequestRepository requestsParam,
                         final UserRepository usersParam) {
        requests = requestsParam;
        users = usersParam;
    }

    @Transactional(readOnly = true)
    public List<FriendResponse> friends(final String actor) {
        return requests.findForUser(actor, FriendRequestStatus.ACCEPTED).stream()
                .map(request -> new FriendResponse(
                        request.getSender().getUserName().equals(actor)
                                ? request.getRecipient().getUserName()
                                : request.getSender().getUserName(), request.getAcceptedAt()))
                .sorted(Comparator.comparing(FriendResponse::username)).toList();
    }

    @Transactional(readOnly = true)
    public FriendInvitationsResponse invitations(final String actor) {
        var pending = requests.findForUser(actor, FriendRequestStatus.PENDING);
        return new FriendInvitationsResponse(pending.stream()
                .filter(request -> request.getRecipient().getUserName().equals(actor))
                .map(this::response).toList(), pending.stream()
                .filter(request -> request.getSender().getUserName().equals(actor))
                .map(this::response).toList());
    }

    @Transactional
    public FriendRequestResponse send(final String actor, final String username) {
        User sender = user(actor);
        User recipient = user(username.strip());
        if (sender.getUserId() == recipient.getUserId()) {
            throw new IllegalArgumentException("You cannot send a friend request to yourself");
        }
        // Serialize both directions of a pair in the same lock order, even before a row exists.
        users.findForUpdate(Math.min(sender.getUserId(), recipient.getUserId())).orElseThrow();
        users.findForUpdate(Math.max(sender.getUserId(), recipient.getUserId())).orElseThrow();
        var existing = requests.findBetween(sender.getUserId(), recipient.getUserId());
        if (existing.isPresent()) {
            boolean accepted = existing.get().getStatus() == FriendRequestStatus.ACCEPTED;
            throw new FriendConflictException(accepted ? "You are already friends"
                    : "A friend request between these users is pending");
        }
        FriendRequest request = new FriendRequest();
        request.setSender(sender);
        request.setRecipient(recipient);
        return response(requests.saveAndFlush(request));
    }

    @Transactional
    public FriendRequestResponse accept(final long id, final String actor) {
        FriendRequest request = locked(id);
        if (!request.getRecipient().getUserName().equals(actor)) {
            throw new AccessDeniedException();
        }
        requirePending(request);
        request.setStatus(FriendRequestStatus.ACCEPTED);
        request.setAcceptedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        return response(request);
    }

    @Transactional
    public void dismiss(final long id, final String actor) {
        FriendRequest request = locked(id);
        if (!request.getRecipient().getUserName().equals(actor)
                && !request.getSender().getUserName().equals(actor)) {
            throw new AccessDeniedException();
        }
        requirePending(request);
        requests.delete(request);
    }

    private User user(final String username) {
        return users.findByUserName(username).orElseThrow(UsernameNotFoundException::new);
    }

    private FriendRequest locked(final long id) {
        return requests.findForUpdate(id).orElseThrow(FriendRequestNotFoundException::new);
    }

    private void requirePending(final FriendRequest request) {
        if (request.getStatus() != FriendRequestStatus.PENDING) {
            throw new FriendConflictException("This friend request has already been accepted");
        }
    }

    private FriendRequestResponse response(final FriendRequest request) {
        return new FriendRequestResponse(request.getRequestId(), request.getSender().getUserName(),
                request.getRecipient().getUserName(), request.getStatus(), request.getCreatedAt(),
                request.getAcceptedAt());
    }
}
