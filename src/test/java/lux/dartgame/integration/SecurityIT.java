package lux.dartgame.integration;

import java.util.List;
import lux.dartgame.dto.CreateGameRequest;
import lux.dartgame.dto.MatchResponse;
import lux.dartgame.dto.PlayedRoundRequest;
import lux.dartgame.dto.UserRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityIT extends AbstractIT {
    @Test
    void unauthenticatedRequestsAreRejected() {
        assertThat(rest().getForEntity(GAMES_URL, String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rest().postForEntity(GAMES_URL,
                new CreateGameRequest("301", List.of()), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(countRows("matches")).isZero();
    }

    @Test
    void nonMembersCannotReadStartScoreAbandonOrDelete() {
        String alice = register("alice");
        String stranger = register("stranger");
        MatchResponse game = create(alice, List.of());
        String url = GAMES_URL + "/" + game.gameId();
        assertThat(rest().exchange(url, HttpMethod.GET,
                new HttpEntity<>(bearer(stranger)), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        for (String action : List.of("start", "abandon")) {
            assertThat(rest().postForEntity(url + "/" + action, authed(stranger, null),
                    String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(rest().exchange(url, HttpMethod.DELETE,
                new HttpEntity<>(bearer(stranger)), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest().postForEntity(url + "/rounds", authed(stranger,
                new PlayedRoundRequest("alice", "20 3", game.currentLegId(), game.revision())),
                String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest().exchange(GAMES_URL, HttpMethod.GET,
                new HttpEntity<>(bearer(stranger)), List.class).getBody()).isEmpty();
    }

    @Test
    void membersCannotManageOwnersGameOrScoreOutOfTurnOrAnotherLeg() {
        String alice = register("alice");
        String bob = register("bob");
        MatchResponse game = create(alice, List.of(new UserRequest("bob")));
        MatchResponse other = create(alice, List.of(new UserRequest("bob")));
        String url = GAMES_URL + "/" + game.gameId();
        assertThat(rest().exchange(url, HttpMethod.DELETE,
                new HttpEntity<>(bearer(bob)), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        game = rest().postForObject(url + "/start", authed(alice, null), MatchResponse.class);
        assertThat(rest().postForEntity(url + "/abandon", authed(bob, null), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest().postForEntity(url + "/rounds", authed(bob,
                new PlayedRoundRequest("bob", "20 3", game.currentLegId(), game.revision())),
                String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest().postForEntity(url + "/rounds", authed(bob,
                new PlayedRoundRequest("alice", "20 3", other.currentLegId(), game.revision())),
                String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc().queryForObject("SELECT SUM(turn) FROM leg_player_stats", Integer.class))
                .isZero();
    }

    @Test
    void administratorCanInspectAndDeletePendingButCannotErasePlayedHistory() {
        String alice = register("alice");
        register("admin");
        jdbc().update("UPDATE users SET role_id = (SELECT role_id FROM roles WHERE role='ADMIN') "
                + "WHERE user_name='admin'");
        String admin = login("admin", PASSWORD);
        MatchResponse pending = create(alice, List.of());
        String url = GAMES_URL + "/" + pending.gameId();
        assertThat(rest().exchange(url, HttpMethod.GET, new HttpEntity<>(bearer(admin)),
                MatchResponse.class).getBody().permissions().canDelete()).isTrue();
        assertThat(rest().exchange(url, HttpMethod.DELETE, new HttpEntity<>(bearer(admin)),
                String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        MatchResponse played = create(alice, List.of());
        url = GAMES_URL + "/" + played.gameId();
        rest().postForObject(url + "/start", authed(alice, null), MatchResponse.class);
        assertThat(rest().exchange(url, HttpMethod.DELETE, new HttpEntity<>(bearer(admin)),
                String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void missingRevisionAndNullPlayersAreBadRequests() {
        String token = register("alice");
        MatchResponse game = create(token, List.of());
        assertThat(rest().postForEntity(GAMES_URL + "/" + game.gameId() + "/rounds",
                authed(token, new PlayedRoundRequest("alice", "", game.currentLegId(), null)),
                String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rest().postForEntity(GAMES_URL, authed(token,
                new CreateGameRequest("301", java.util.Arrays.asList((UserRequest) null))),
                String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private MatchResponse create(final String token, final List<UserRequest> players) {
        return rest().postForObject(GAMES_URL,
                authed(token, new CreateGameRequest("301", players)), MatchResponse.class);
    }
}
