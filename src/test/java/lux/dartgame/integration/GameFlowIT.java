package lux.dartgame.integration;

import java.util.List;
import java.util.Set;

import lux.dartgame.dto.CreateSessionRequest;
import lux.dartgame.dto.GameRequest;
import lux.dartgame.dto.GameResponse;
import lux.dartgame.dto.GametypeResponse;
import lux.dartgame.dto.PlayedRoundRequest;
import lux.dartgame.dto.PlayerStatResponse;
import lux.dartgame.dto.SessionResponse;
import lux.dartgame.dto.UserRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage of the session and game endpoints. Each test walks a
 * real HTTP request into PostgreSQL and then asserts on the rows that were
 * written, or on the rows that the following request reads back.
 */
class GameFlowIT extends AbstractIT {

    private static final String ALICE = "alice-flow";
    private static final String BOB = "bob-flow";

    @Test
    void createSession_persistsSessionPlayersGameAndGameStats() {
        String token = register(ALICE);
        register(BOB);

        ResponseEntity<SessionResponse> response = rest().postForEntity(
                SESSION_URL,
                authed(token, new CreateSessionRequest(
                        Set.of(new UserRequest(ALICE), new UserRequest(BOB)),
                        List.of(new GameRequest(TARGET_301)))),
                SessionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        SessionResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.id()).isPositive();
        assertThat(body.isActive()).isTrue();
        assertThat(body.gametypes())
                .extracting(GametypeResponse::gamemode)
                .containsExactly(TARGET_301);

        long sessionId = body.id();

        assertThat(jdbc().queryForObject(
                "SELECT owner_id FROM sessions WHERE session_id = ?", Long.class, sessionId))
                .isEqualTo(userId(ALICE));
        assertThat(jdbc().queryForObject(
                "SELECT is_active FROM sessions WHERE session_id = ?", Boolean.class, sessionId))
                .isTrue();

        Integer playerCount = jdbc().queryForObject(
                "SELECT COUNT(*) FROM session_mm_users WHERE session_id = ?",
                Integer.class, sessionId);
        assertThat(playerCount).isEqualTo(2);

        Integer gameCount = jdbc().queryForObject(
                "SELECT COUNT(*) FROM games WHERE session_id = ?", Integer.class, sessionId);
        assertThat(gameCount).isEqualTo(1);

        assertThat(jdbc().queryForObject(
                "SELECT nr_of_players FROM games WHERE session_id = ?", Integer.class, sessionId))
                .isEqualTo(2);

        List<String> positions = jdbc().queryForList(
                "SELECT position FROM gamestats WHERE game_id = "
                        + "(SELECT game_id FROM games WHERE session_id = ?) ORDER BY position",
                String.class, sessionId);
        assertThat(positions).containsExactly("0", "1");
    }

    @Test
    void getSessions_returnsSessionsCreatedInTheDatabase() {
        String token = register(ALICE);

        SessionResponse created = createSession(token, List.of(new GameRequest(TARGET_501)));

        ResponseEntity<List> response = rest().exchange(
                SESSION_URL + "?username=" + ALICE, HttpMethod.GET,
                new HttpEntity<>(bearer(token)), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).toString())
                .contains("id=" + created.id())
                .contains(TARGET_501);
    }

    @Test
    void playRound_updatesGameStatRowAndAdvancesTurn() {
        String token = register(ALICE);
        register(BOB);
        SessionResponse session = createSession(token,
                List.of(new GameRequest(TARGET_301)),
                Set.of(new UserRequest(ALICE), new UserRequest(BOB)));

        GameResponse started = startGame(token, session.id(), TARGET_301);
        long gameId = started.gameId();
        assertThat(started.isFinished()).isFalse();

        // "20 3" is a triple twenty: 60 points, one triple-20.
        GameResponse afterFirst = playRound(token, session.id(), gameId, ALICE, "20 3");
        assertThat(afterFirst.isFinished()).isFalse();
        assertThat(afterFirst.turn()).isEqualTo(BOB);

        Integer points = jdbc().queryForObject(
                "SELECT points FROM gamestats WHERE user_id = ? AND game_id = ?",
                Integer.class, userId(ALICE), gameId);
        assertThat(points).isEqualTo(241);

        Integer turns = jdbc().queryForObject(
                "SELECT turn FROM gamestats WHERE user_id = ? AND game_id = ?",
                Integer.class, userId(ALICE), gameId);
        assertThat(turns).isEqualTo(1);

        Integer triples = jdbc().queryForObject(
                "SELECT triple20s FROM gamestats WHERE user_id = ? AND game_id = ?",
                Integer.class, userId(ALICE), gameId);
        assertThat(triples).isEqualTo(1);

        // The stats endpoint reads the same row back out of the database.
        ResponseEntity<List> stats = rest().exchange(
                gamesUrl(session.id(), gameId) + "/stats", HttpMethod.GET,
                new HttpEntity<>(bearer(token)), List.class);
        assertThat(stats.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stats.getBody()).hasSize(2);
        assertThat(stats.getBody().get(0).toString())
                .contains("userName=" + ALICE)
                .contains("startingPoints=301");
    }

    @Test
    void playRound_toExactTarget_setsWinnerAndAggregatesPlayerStats() {
        String token = register(ALICE);
        SessionResponse session = createSession(token, List.of(new GameRequest(TARGET_301)));

        GameResponse started = startGame(token, session.id(), TARGET_301);
        long gameId = started.gameId();
        assertThat(started.turn()).isEqualTo(ALICE);

        // 5 x triple twenty leaves 1, then a single 1 finishes on zero.
        for (final String score : List.of("20 3", "20 3", "20 3", "20 3", "20 3")) {
            playRound(token, session.id(), gameId, ALICE, score);
        }
        assertThat(pointsIn(gameId, ALICE)).isEqualTo(1);

        GameResponse finalRound = playRound(token, session.id(), gameId, ALICE, "1 1");
        assertThat(finalRound.isFinished()).isTrue();
        assertThat(finalRound.turn()).isEqualTo(ALICE);

        assertThat(jdbc().queryForObject(
                "SELECT winner_id FROM games WHERE game_id = ?", Long.class, gameId))
                .isEqualTo(userId(ALICE));
        assertThat(pointsIn(gameId, ALICE)).isZero();

        // Finishing the game rolled the GameStat rows into the PlayerStat row.
        assertThat(playedGames(ALICE)).isEqualTo(1);
        assertThat(wonGames(ALICE)).isEqualTo(1);
        assertThat(jdbc().queryForObject(
                "SELECT triple20s FROM playerstats WHERE player_id = ?",
                Integer.class, userId(ALICE))).isEqualTo(5);

        // The aggregate is readable over HTTP as well.
        ResponseEntity<List> response = rest().exchange(
                PLAYER_STATS_URL, HttpMethod.POST,
                authed(token, List.of(new UserRequest(ALICE))), List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).toString())
                .contains("wonGames=1")
                .contains("playedGames=1");
    }

    @Test
    void playRound_thatOvershootsTarget_keepsPreviousPointsInTheDatabase() {
        String token = register(ALICE);
        SessionResponse session = createSession(token, List.of(new GameRequest(TARGET_301)));
        long gameId = startGame(token, session.id(), TARGET_301).gameId();

        playRound(token, session.id(), gameId, ALICE, "20 3");
        assertThat(pointsIn(gameId, ALICE)).isEqualTo(241);

        for (int round = 0; round < 4; round++) {
            playRound(token, session.id(), gameId, ALICE, "20 3");
        }
        assertThat(pointsIn(gameId, ALICE)).isEqualTo(1);

        // Subtracting 20 from 1 would go below zero, so the round scores nothing.
        playRound(token, session.id(), gameId, ALICE, "20 1");

        // Bust: points stay at 1 and no winner is recorded.
        assertThat(pointsIn(gameId, ALICE)).isEqualTo(1);
        assertThat(jdbc().queryForObject(
                "SELECT winner_id FROM games WHERE game_id = ?", Long.class, gameId))
                .isNull();
    }

    @Test
    void deleteSession_removesSessionGamesAndGameStatsFromTheDatabase() {
        String token = register(ALICE);
        register(BOB);
        SessionResponse session = createSession(token,
                List.of(new GameRequest(TARGET_301)),
                Set.of(new UserRequest(ALICE), new UserRequest(BOB)));
        long sessionId = session.id();

        assertThat(countRows("games")).isEqualTo(1);
        assertThat(countRows("gamestats")).isEqualTo(2);

        ResponseEntity<Void> response = rest().exchange(
                SESSION_URL + "/" + sessionId, HttpMethod.DELETE,
                new HttpEntity<>(bearer(token)), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(countRows("sessions")).isZero();
        assertThat(countRows("games")).isZero();
        assertThat(countRows("gamestats")).isZero();
        assertThat(countRows("session_mm_users")).isZero();
        // The users themselves survive; only the aggregate was removed.
        assertThat(countRows("users")).isEqualTo(2);
    }

    @Test
    void finishSession_marksSessionInactiveInTheDatabase() {
        String token = register(ALICE);
        SessionResponse session = createSession(token, List.of(new GameRequest(TARGET_301)));
        long sessionId = session.id();

        // Finish the only game so the session is kept, just deactivated.
        long gameId = startGame(token, sessionId, TARGET_301).gameId();
        for (final String score : List.of("20 3", "20 3", "20 3", "20 3", "20 3")) {
            playRound(token, sessionId, gameId, ALICE, score);
        }
        playRound(token, sessionId, gameId, ALICE, "1 1");

        ResponseEntity<Void> response = rest().exchange(
                SESSION_URL + "/" + sessionId, HttpMethod.PUT,
                new HttpEntity<>(bearer(token)), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc().queryForObject(
                "SELECT is_active FROM sessions WHERE session_id = ?", Boolean.class, sessionId))
                .isFalse();
        assertThat(countRows("sessions")).isEqualTo(1);
        assertThat(countRows("games")).isEqualTo(1);
    }

    @Test
    void startGame_byNonMember_isForbidden() {
        String aliceToken = register(ALICE);
        String bobToken = register(BOB);
        register("carol-flow");
        SessionResponse session = createSession(aliceToken, List.of(new GameRequest(TARGET_301)));

        ResponseEntity<String> response = rest().exchange(
                gamesUrl(session.id()) + "?gametype=" + TARGET_301, HttpMethod.GET,
                new HttpEntity<>(bearer(bobToken)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createSession_withoutToken_isUnauthorizedAndPersistsNothing() {
        register(ALICE);

        ResponseEntity<String> response = rest().postForEntity(
                SESSION_URL,
                new CreateSessionRequest(Set.of(), List.of(new GameRequest(TARGET_301))),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(countRows("sessions")).isZero();
    }

    @Test
    void gameModeList_isReadBackFromTheDatabase() {
        String token = register(ALICE);

        ResponseEntity<List> response = rest().exchange(
                GAMEMODE_URL, HttpMethod.GET, new HttpEntity<>(bearer(token)), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(countRows("gametypes"));
        assertThat(response.getBody().get(0).toString()).contains("gamemode=");
    }

    /**
     * Creates a session for the token's owner with no extra players, so the
     * owner is the only participant.
     */
    private SessionResponse createSession(final String token,
                                          final List<GameRequest> games) {
        return createSession(token, games, Set.of());
    }

    private SessionResponse createSession(final String token,
                                          final List<GameRequest> games,
                                          final Set<UserRequest> players) {
        ResponseEntity<SessionResponse> response = rest().postForEntity(
                SESSION_URL, authed(token, new CreateSessionRequest(players, games)),
                SessionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private GameResponse startGame(final String token,
                                   final long sessionId,
                                   final String gametype) {
        ResponseEntity<GameResponse> response = rest().exchange(
                gamesUrl(sessionId) + "?gametype=" + gametype, HttpMethod.GET,
                new HttpEntity<>(bearer(token)), GameResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private GameResponse playRound(final String token,
                                   final long sessionId,
                                   final long gameId,
                                   final String username,
                                   final String score) {
        ResponseEntity<GameResponse> response = rest().exchange(
                gamesUrl(sessionId), HttpMethod.PUT,
                authed(token, new PlayedRoundRequest(username, score, gameId)),
                GameResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private static String gamesUrl(final long sessionId) {
        return SESSION_URL + "/" + sessionId + "/games";
    }

    private static String gamesUrl(final long sessionId, final long gameId) {
        return gamesUrl(sessionId) + "/" + gameId;
    }

    private long userId(final String username) {
        return jdbc().queryForObject(
                "SELECT user_id FROM users WHERE user_name = ?", Long.class, username);
    }

    private int pointsIn(final long gameId, final String username) {
        return jdbc().queryForObject(
                "SELECT points FROM gamestats WHERE user_id = ? AND game_id = ?",
                Integer.class, userId(username), gameId);
    }

    private int playedGames(final String username) {
        return jdbc().queryForObject(
                "SELECT played_games FROM playerstats WHERE player_id = ?",
                Integer.class, userId(username));
    }

    private int wonGames(final String username) {
        return jdbc().queryForObject(
                "SELECT won_games FROM playerstats WHERE player_id = ?",
                Integer.class, userId(username));
    }
}
