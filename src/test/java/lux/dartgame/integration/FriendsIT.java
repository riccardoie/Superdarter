package lux.dartgame.integration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lux.dartgame.dto.FriendInvitationsResponse;
import lux.dartgame.dto.FriendRequestResponse;
import lux.dartgame.dto.FriendResponse;
import lux.dartgame.dto.SendFriendRequest;
import lux.dartgame.model.FriendRequestStatus;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FriendsIT extends AbstractIT {
    private static final String FRIENDS = "/api/friends";
    private static final String REQUESTS = FRIENDS + "/requests";

    @Test
    void friendsRequireExplicitRecipientAcceptanceAndPersistForBothUsers() {
        String alice = register("alice");
        String bob = register("bob");
        String carol = register("carol");
        FriendRequestResponse request = send(alice, " bob ");
        assertThat(request.sender()).isEqualTo("alice");
        assertThat(request.recipient()).isEqualTo("bob");
        assertThat(request.status()).isEqualTo(FriendRequestStatus.PENDING);
        assertThat(request.createdAt()).isNotNull();
        assertThat(request.acceptedAt()).isNull();
        assertThat(friends(alice)).isEmpty();
        assertThat(friends(bob)).isEmpty();
        assertThat(invitations(alice).incoming()).isEmpty();
        assertThat(invitations(alice).outgoing()).containsExactly(request);
        assertThat(invitations(bob).incoming()).containsExactly(request);
        assertThat(invitations(bob).outgoing()).isEmpty();
        assertThat(invitations(carol).incoming()).isEmpty();
        assertThat(invitations(carol).outgoing()).isEmpty();
        var accepted = rest().postForEntity(acceptUrl(request), authed(bob, null),
                FriendRequestResponse.class);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accepted.getBody().status()).isEqualTo(FriendRequestStatus.ACCEPTED);
        assertThat(accepted.getBody().acceptedAt()).isAfterOrEqualTo(request.createdAt());
        assertThat(friends(alice)).containsExactly(new FriendResponse("bob",
                accepted.getBody().acceptedAt()));
        assertThat(friends(bob)).containsExactly(new FriendResponse("alice",
                accepted.getBody().acceptedAt()));
        assertThat(friends(carol)).isEmpty();
        assertThat(invitations(alice).outgoing()).isEmpty();
        assertThat(invitations(bob).incoming()).isEmpty();
        assertThat(countRows("friend_requests")).isEqualTo(1);
    }

    @Test
    void rejectsSelfMissingAndInvalidRecipients() {
        String alice = register("alice");
        for (String name : List.of("alice", " alice ", "", "   ", "x".repeat(256))) {
            assertThat(rest().postForEntity(REQUESTS, authed(alice, new SendFriendRequest(name)),
                    String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(rest().postForEntity(REQUESTS, authed(alice, Map.of()), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rest().postForEntity(REQUESTS, authed(alice, new SendFriendRequest("missing")),
                String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(countRows("friend_requests")).isZero();
    }

    @Test
    void listsOnlyTheCallersFriendsWithoutPrivateAccountFields() {
        String alice = register("alice");
        String bob = register("bob");
        String carol = register("carol");
        FriendRequestResponse first = send(alice, "carol");
        FriendRequestResponse second = send(bob, "alice");
        rest().postForEntity(acceptUrl(first), authed(carol, null), String.class);
        rest().postForEntity(acceptUrl(second), authed(alice, null), String.class);
        assertThat(friends(alice)).extracting(FriendResponse::username)
                .containsExactly("bob", "carol");
        assertThat(friends(bob)).extracting(FriendResponse::username).containsExactly("alice");
        String body = rest().exchange(FRIENDS + "?username=bob", HttpMethod.GET,
                authed(alice, null), String.class).getBody();
        assertThat(body).contains("bob", "carol").doesNotContain("password", "email", "userId");
    }

    @Test
    void senderAndOtherUsersCannotAcceptAndDuplicatesDoNotImplicitlyAccept() {
        String alice = register("alice");
        String bob = register("bob");
        String carol = register("carol");
        FriendRequestResponse request = send(alice, "bob");
        for (String token : List.of(alice, carol)) {
            assertThat(rest().postForEntity(acceptUrl(request), authed(token, null), String.class)
                    .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(rest().postForEntity(REQUESTS, authed(alice, new SendFriendRequest("bob")),
                String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(rest().postForEntity(REQUESTS, authed(bob, new SendFriendRequest("alice")),
                String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(friends(alice)).isEmpty();
        assertThat(countRows("friend_requests")).isEqualTo(1);
        rest().postForEntity(acceptUrl(request), authed(bob, null), String.class);
        assertThat(rest().postForEntity(acceptUrl(request), authed(bob, null), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        for (var entry : Map.of(alice, "bob", bob, "alice").entrySet()) {
            assertThat(rest().postForEntity(REQUESTS,
                    authed(entry.getKey(), new SendFriendRequest(entry.getValue())), String.class)
                    .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Test
    void pendingRequestsCanBeDeclinedOrWithdrawnButNotByAnUnrelatedUser() {
        String alice = register("alice");
        String bob = register("bob");
        String carol = register("carol");
        FriendRequestResponse request = send(alice, "bob");
        assertThat(dismiss(carol, request)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(dismiss(bob, request)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(invitations(alice).outgoing()).isEmpty();
        assertThat(invitations(bob).incoming()).isEmpty();
        FriendRequestResponse retry = send(alice, "bob");
        assertThat(retry.requestId()).isNotEqualTo(request.requestId());
        assertThat(dismiss(alice, retry)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(friends(alice)).isEmpty();
        assertThat(countRows("friend_requests")).isZero();
        assertThat(rest().postForEntity(acceptUrl(retry), authed(bob, null), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void acceptedFriendshipsCannotBeDeletedAsPendingInvitations() {
        String alice = register("alice");
        String bob = register("bob");
        FriendRequestResponse request = send(alice, "bob");
        rest().postForEntity(acceptUrl(request), authed(bob, null), String.class);
        assertThat(dismiss(alice, request)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(dismiss(bob, request)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(friends(alice)).hasSize(1);
        assertThat(friends(bob)).hasSize(1);
    }

    @Test
    void unauthenticatedCallsCannotReadOrModifyFriends() {
        assertThat(rest().getForEntity(FRIENDS, String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rest().getForEntity(REQUESTS, String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rest().postForEntity(REQUESTS, new SendFriendRequest("bob"), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rest().postForEntity(REQUESTS + "/1/accept", null, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rest().exchange(REQUESTS + "/1", HttpMethod.DELETE, null, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void concurrentCrossedInvitationsCreateOnePendingRequestWithoutAutomaticFriendship()
            throws Exception {
        String alice = register("alice");
        String bob = register("bob");
        var barrier = new CyclicBarrier(2);
        Callable<HttpStatus> first = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return HttpStatus.valueOf(rest().postForEntity(REQUESTS,
                    authed(alice, new SendFriendRequest("bob")), String.class)
                    .getStatusCode().value());
        };
        Callable<HttpStatus> second = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return HttpStatus.valueOf(rest().postForEntity(REQUESTS,
                    authed(bob, new SendFriendRequest("alice")), String.class)
                    .getStatusCode().value());
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.of(first, second), 20, TimeUnit.SECONDS);
            assertThat(List.of(results.get(0).get(), results.get(1).get()))
                    .containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
        }
        assertThat(countRows("friend_requests")).isEqualTo(1);
        assertThat(friends(alice)).isEmpty();
        assertThat(friends(bob)).isEmpty();
    }

    @Test
    void concurrentAcceptsCreateJustOneMutualFriendship() throws Exception {
        String alice = register("alice");
        String bob = register("bob");
        FriendRequestResponse request = send(alice, "bob");
        var barrier = new CyclicBarrier(2);
        Callable<HttpStatus> accept = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return HttpStatus.valueOf(rest().postForEntity(acceptUrl(request), authed(bob, null),
                    String.class).getStatusCode().value());
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.of(accept, accept), 20, TimeUnit.SECONDS);
            assertThat(List.of(results.get(0).get(), results.get(1).get()))
                    .containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
        }
        assertThat(friends(alice)).hasSize(1);
        assertThat(friends(bob)).hasSize(1);
        assertThat(countRows("friend_requests")).isEqualTo(1);
    }

    @Test
    void databaseEnforcesPairUniquenessNoSelfRequestsAndUserDeletionCleanup() {
        String alice = register("alice");
        register("bob");
        send(alice, "bob");
        Long aliceId = jdbc().queryForObject(
                "SELECT user_id FROM users WHERE user_name='alice'", Long.class);
        Long bobId = jdbc().queryForObject(
                "SELECT user_id FROM users WHERE user_name='bob'", Long.class);
        assertThatThrownBy(() -> jdbc().update(
                "INSERT INTO friend_requests(sender_id, recipient_id) VALUES (?, ?)", bobId, aliceId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc().update(
                "INSERT INTO friend_requests(sender_id, recipient_id) VALUES (?, ?)", aliceId, aliceId))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc().update("DELETE FROM users WHERE user_id=?", bobId);
        assertThat(countRows("friend_requests")).isZero();
        assertThat(countRows("users")).isEqualTo(1);
    }

    private FriendRequestResponse send(final String token, final String username) {
        var response = rest().postForEntity(REQUESTS, authed(token, new SendFriendRequest(username)),
                FriendRequestResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private FriendResponse[] friends(final String token) {
        return rest().exchange(FRIENDS, HttpMethod.GET, authed(token, null), FriendResponse[].class)
                .getBody();
    }

    private FriendInvitationsResponse invitations(final String token) {
        return rest().exchange(REQUESTS, HttpMethod.GET, authed(token, null),
                FriendInvitationsResponse.class).getBody();
    }

    private String acceptUrl(final FriendRequestResponse request) {
        return REQUESTS + "/" + request.requestId() + "/accept";
    }

    private HttpStatus dismiss(final String token, final FriendRequestResponse request) {
        return HttpStatus.valueOf(rest().exchange(REQUESTS + "/" + request.requestId(),
                HttpMethod.DELETE, authed(token, null), String.class).getStatusCode().value());
    }
}
