package lux.dartgame.integration;

import java.util.List;
import java.util.Set;

import lux.dartgame.dto.CreateSessionRequest;
import lux.dartgame.dto.GameRequest;
import lux.dartgame.dto.LoginRequest;
import lux.dartgame.dto.PlayedRoundRequest;
import lux.dartgame.dto.SessionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage of the authorisation rules. Every request travels the
 * real security filter chain, and each test then inspects the database to show
 * that a rejected request wrote nothing.
 */
class SecurityIT extends AbstractIT {

    private static final String ALICE = "alice-sec";
    private static final String BOB = "bob-sec";
    private static final String CAROL = "carol-sec";

    @Test
    void gamemodeList_isPublicOnlyAfterLogin() {
        register(ALICE);

        ResponseEntity<String> anonymous = rest().getForEntity(GAMEMODE_URL, String.class);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous.getBody()).isNull();

        String token = login(ALICE, PASSWORD);
        ResponseEntity<String> authenticated = rest().exchange(
                GAMEMODE_URL, HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);

        assertThat(authenticated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(authenticated.getBody()).contains(TARGET_501);
    }

    @Test
    void registerAndLogin_arePublic() {
        register(ALICE);

        ResponseEntity<String> response = rest().postForEntity(
                AUTH_URL + "/login",
                new LoginRequest(ALICE, PASSWORD), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("token");
    }

    @Test
    void deleteSession_byNonOwner_isForbiddenAndTheRowSurvives() {
        String aliceToken = register(ALICE);
        String bobToken = register(BOB);

        ResponseEntity<SessionResponse> created = rest().postForEntity(
                SESSION_URL,
                authed(aliceToken, new CreateSessionRequest(
                        Set.of(), List.of(new GameRequest(TARGET_301)))),
                SessionResponse.class);
        long sessionId = created.getBody().id();

        ResponseEntity<String> response = rest().exchange(
                SESSION_URL + "/" + sessionId, HttpMethod.DELETE,
                new HttpEntity<>(bearer(bobToken)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(countRows("sessions")).isEqualTo(1);
        assertThat(jdbc().queryForObject(
                "SELECT owner_id FROM sessions WHERE session_id = ?", Long.class, sessionId))
                .isEqualTo(userId(ALICE));
    }

    @Test
    void addGames_byNonOwner_isForbiddenAndTheRowSurvives() {
        String aliceToken = register(ALICE);
        String bobToken = register(BOB);

        ResponseEntity<SessionResponse> created = rest().postForEntity(
                SESSION_URL,
                authed(aliceToken, new CreateSessionRequest(
                        Set.of(), List.of(new GameRequest(TARGET_301)))),
                SessionResponse.class);
        long sessionId = created.getBody().id();

        ResponseEntity<String> response = rest().exchange(
                SESSION_URL + "/" + sessionId, HttpMethod.POST,
                authed(bobToken, List.of(new GameRequest(TARGET_501))), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // Still only the one 301 game the owner created.
        assertThat(countRows("games")).isEqualTo(1);
        assertThat(jdbc().queryForObject(
                "SELECT COUNT(*) FROM games g JOIN gametypes t ON t.gametype_id = g.gametype_id"
                        + " WHERE g.session_id = ? AND t.gametype = ?",
                Integer.class, sessionId, TARGET_501))
                .isZero();
    }

    @Test
    void getSessions_forUnknownUser_isNotFound() {
        String token = register(ALICE);

        ResponseEntity<String> response = rest().exchange(
                SESSION_URL + "?username=" + CAROL, HttpMethod.GET,
                new HttpEntity<>(bearer(token)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("User not found");
    }

    @Test
    void getSessions_forUserWithoutSessions_isNoContent() {
        String token = register(ALICE);

        ResponseEntity<String> response = rest().exchange(
                SESSION_URL + "?username=" + ALICE, HttpMethod.GET,
                new HttpEntity<>(bearer(token)), String.class);

        // The application maps "no sessions" to 204, not 404.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void startGame_byNonMember_isForbiddenAndNoTurnIsConsumed() {
        String aliceToken = register(ALICE);
        register(BOB);
        register(CAROL);

        ResponseEntity<SessionResponse> created = rest().postForEntity(
                SESSION_URL,
                authed(aliceToken, new CreateSessionRequest(
                        Set.of(), List.of(new GameRequest(TARGET_301)))),
                SessionResponse.class);
        long sessionId = created.getBody().id();
        long gameId = jdbc().queryForObject(
                "SELECT game_id FROM games WHERE session_id = ?", Long.class, sessionId);

        ResponseEntity<String> response = rest().exchange(
                SESSION_URL + "/" + sessionId + "/games?gametype=" + TARGET_301,
                HttpMethod.GET, new HttpEntity<>(bearer(login(CAROL, PASSWORD))), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // The player's GameStat is untouched at turn 0 with 301 remaining.
        assertThat(jdbc().queryForObject(
                "SELECT SUM(turn) FROM gamestats WHERE game_id = ?", Integer.class, gameId))
                .isZero();
        assertThat(jdbc().queryForObject(
                "SELECT SUM(points) FROM gamestats WHERE game_id = ?", Integer.class, gameId))
                .isEqualTo(301);
    }

    @Test
    void playRound_withATokenForAnotherUser_isRejected() {
        String aliceToken = register(ALICE);
        String bobToken = register(BOB);

        ResponseEntity<SessionResponse> created = rest().postForEntity(
                SESSION_URL,
                authed(aliceToken, new CreateSessionRequest(
                        Set.of(), List.of(new GameRequest(TARGET_301)))),
                SessionResponse.class);
        long sessionId = created.getBody().id();
        long gameId = jdbc().queryForObject(
                "SELECT game_id FROM games WHERE session_id = ?", Long.class, sessionId);

        // Bob is not a member of Alice's session, so he may not play her game.
        ResponseEntity<String> response = rest().exchange(
                SESSION_URL + "/" + sessionId + "/games", HttpMethod.PUT,
                authed(bobToken, new PlayedRoundRequest(BOB, "20 3", gameId)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc().queryForObject(
                "SELECT SUM(points) FROM gamestats WHERE game_id = ?", Integer.class, gameId))
                .isEqualTo(301);
    }

    private long userId(final String username) {
        return jdbc().queryForObject(
                "SELECT user_id FROM users WHERE user_name = ?", Long.class, username);
    }
}
