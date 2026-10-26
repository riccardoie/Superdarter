package lux.dartgame.integration;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import lux.dartgame.dto.CreateGameRequest;
import lux.dartgame.dto.MatchResponse;
import lux.dartgame.dto.PlayedRoundRequest;
import lux.dartgame.dto.UserRequest;
import lux.dartgame.model.MatchStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GameFlowIT extends AbstractIT {
    @ParameterizedTest
    @ValueSource(strings = {"301", "501"})
    void createInitializesOneLegAndStableParticipants(final String gametype) {
        String alice = register("alice");
        String bob = register("bob");
        MatchResponse game = create(alice, gametype, List.of(new UserRequest("bob")));
        assertThat(game.status()).isEqualTo(MatchStatus.PENDING);
        assertThat(game.owner()).isEqualTo("alice");
        assertThat(game.format()).isEqualTo("SINGLE_LEG");
        assertThat(game.players()).extracting(MatchResponse.Player::username)
                .containsExactly("alice", "bob");
        assertThat(game.players()).allMatch(p -> p.points() == Integer.parseInt(gametype));
        assertThat(game.legs()).hasSize(1);
        assertThat(countRows("matches")).isEqualTo(1);
        assertThat(countRows("legs")).isEqualTo(1);
        assertThat(countRows("match_participants")).isEqualTo(2);
        assertThat(countRows("leg_player_stats")).isEqualTo(2);
        assertThat(get(bob, game.gameId()).permissions().canDelete()).isFalse();
        ResponseEntity<List> listed = rest().exchange(GAMES_URL, HttpMethod.GET,
                new HttpEntity<>(bearer(bob)), List.class);
        assertThat(listed.getBody()).hasSize(1);
        assertThat(rest().exchange(GAMES_URL + "?gametype=999", HttpMethod.GET,
                new HttpEntity<>(bearer(bob)), List.class).getBody()).isEmpty();
    }

    @Test
    void sharedDeviceScoringReloadAndEmptyRounds() {
        String alice = register("alice");
        String bob = register("bob");
        MatchResponse pending = create(alice, "501", List.of(new UserRequest("bob")));
        MatchResponse game = start(bob, pending.gameId());
        assertThat(game.status()).isEqualTo(MatchStatus.IN_PROGRESS);
        long startedRevision = game.revision();
        assertThat(start(alice, game.gameId()).revision()).isEqualTo(startedRevision);
        game = play(bob, game, "alice", "20 3");
        assertThat(game.players().getFirst().points()).isEqualTo(441);
        assertThat(game.players().getFirst().triple20s()).isEqualTo(1);
        assertThat(game.turn()).isEqualTo("bob");
        game = play(alice, game, "bob", "");
        assertThat(game.players().get(1).points()).isEqualTo(501);
        assertThat(game.players().get(1).turns()).isEqualTo(1);
        MatchResponse reloaded = get(bob, game.gameId());
        assertThat(reloaded).usingRecursiveComparison().ignoringFields("permissions")
                .isEqualTo(game);
        assertThat(reloaded.turn()).isEqualTo("alice");
        assertThat(reloaded.startingPoints()).isEqualTo(501);
    }

    @Test
    void doubleOutRestoresBustsAndCompletesExactlyOnce() {
        String token = register("alice");
        MatchResponse game = start(token, create(token, "301", List.of()).gameId());
        game = play(token, game, "alice", "20 3 20 3 20 3"); // 121
        game = play(token, game, "alice", "20 3 20 3"); // leave 1: bust entire turn
        assertThat(game.players().getFirst().points()).isEqualTo(121);
        game = play(token, game, "alice", "20 3 19 1"); // 42
        game = play(token, game, "alice", "20 1 20 1"); // 2
        game = play(token, game, "alice", "2 1"); // non-double zero: bust
        assertThat(game.players().getFirst().points()).isEqualTo(2);
        MatchResponse beforeWin = game;
        game = play(token, game, "alice", "1 2");
        assertThat(game.status()).isEqualTo(MatchStatus.COMPLETED);
        assertThat(game.winner()).isEqualTo("alice");
        assertThat(game.turn()).isNull();
        assertThat(game.finishedAt()).isNotNull();
        assertThat(game.players().getFirst().points()).isZero();
        assertThat(game.players().getFirst().highestCheckout()).isEqualTo(2);
        assertThat(game.players().getFirst().legsWon()).isEqualTo(1);
        assertThat(game.legs().getFirst().status()).isEqualTo(MatchStatus.COMPLETED);
        assertThat(round(token, beforeWin, "alice", "1 2").getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        var total = jdbc().queryForMap("SELECT * FROM playerstats");
        assertThat(total.get("played_matches")).isEqualTo(1);
        assertThat(total.get("won_matches")).isEqualTo(1);
        assertThat(total.get("played_legs")).isEqualTo(1);
        assertThat(total.get("won_legs")).isEqualTo(1);
        assertThat(((Number) total.get("avg_points")).floatValue()).isEqualTo(301f / 6);
        assertThat(rest().exchange(GAMES_URL + "/" + game.gameId(), HttpMethod.DELETE,
                new HttpEntity<>(bearer(token)), String.class).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void abandonmentPreservesHistoryWithoutAwardingStats() {
        String token = register("alice");
        MatchResponse game = start(token, create(token, "501", List.of()).gameId());
        game = play(token, game, "alice", "20 3");
        var abandoned = rest().postForEntity(GAMES_URL + "/" + game.gameId() + "/abandon",
                authed(token, null), MatchResponse.class);
        assertThat(abandoned.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(abandoned.getBody().status()).isEqualTo(MatchStatus.ABANDONED);
        assertThat(abandoned.getBody().winner()).isNull();
        assertThat(abandoned.getBody().players().getFirst().points()).isEqualTo(441);
        assertThat(round(token, game, "alice", "20 3").getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc().queryForObject("SELECT played_matches FROM playerstats", Integer.class))
                .isZero();
        assertThat(countRows("legs")).isEqualTo(1);
    }

    @Test
    void deletingPendingGameCascadesOnlyItsOwnRows() {
        String token = register("alice");
        MatchResponse game = create(token, "301", List.of());
        assertThat(rest().exchange(GAMES_URL + "/" + game.gameId(), HttpMethod.DELETE,
                new HttpEntity<>(bearer(token)), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        for (String table : List.of("matches", "match_participants", "legs", "leg_player_stats")) {
            assertThat(countRows(table)).isZero();
        }
        assertThat(countRows("users")).isEqualTo(1);
        assertThat(countRows("playerstats")).isEqualTo(1);
    }

    @Test
    void tenPlayersIncludesOwnerAndDuplicateNamesAreNotAddedTwice() {
        String token = register("alice");
        List<UserRequest> others = java.util.stream.IntStream.rangeClosed(1, 10)
                .mapToObj(i -> {
                    register("player" + i);
                    return new UserRequest("player" + i);
                }).toList();
        MatchResponse game = create(token, "501", others.subList(0, 9));
        assertThat(game.players()).hasSize(10);
        var tooMany = rest().postForEntity(GAMES_URL,
                authed(token, new CreateGameRequest("501", others)), String.class);
        assertThat(tooMany.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        MatchResponse deduplicated = create(token, "301",
                List.of(new UserRequest("alice"), new UserRequest("player1"),
                        new UserRequest("player1")));
        assertThat(deduplicated.players()).hasSize(2);
    }

    @Test
    void staleAndConcurrentSoloRoundsCannotAdvanceTwice() throws Exception {
        String token = register("alice");
        MatchResponse game = start(token, create(token, "501", List.of()).gameId());
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<HttpStatus> submit = () -> HttpStatus.valueOf(
                    round(token, game, "alice", "20 3").getStatusCode().value());
            var results = executor.invokeAll(List.of(submit, submit));
            assertThat(List.of(results.get(0).get(), results.get(1).get()))
                    .containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
        }
        MatchResponse fresh = get(token, game.gameId());
        assertThat(fresh.players().getFirst().turns()).isEqualTo(1);
        assertThat(fresh.players().getFirst().points()).isEqualTo(441);
    }

    @Test
    void invalidDartsRollbackAndCannotScoreBeforeStart() {
        String token = register("alice");
        MatchResponse game = create(token, "301", List.of());
        assertThat(round(token, game, "alice", "20 3").getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        game = start(token, game.gameId());
        assertThat(round(token, game, "alice", "20 3 20 3 20 3 1 1").getStatusCode())
                .isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(get(token, game.gameId())).isEqualTo(game);
    }

    @Test
    void simultaneousCompletionsAcrossMatchesDoNotLosePlayerTotals() throws Exception {
        String token = register("alice");
        java.util.ArrayList<MatchResponse> games = new java.util.ArrayList<>();
        for (int i = 0; i < 2; i++) {
            MatchResponse game = start(token, create(token, "301", List.of()).gameId());
            game = play(token, game, "alice", "20 3 20 3 20 3");
            game = play(token, game, "alice", "20 3 19 3 2 1");
            games.add(game);
        }
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Callable<HttpStatus>> submissions = games.stream()
                    .<Callable<HttpStatus>>map(game -> () -> HttpStatus.valueOf(
                            round(token, game, "alice", "1 2").getStatusCode().value())).toList();
            for (var result : executor.invokeAll(submissions)) {
                assertThat(result.get()).isEqualTo(HttpStatus.OK);
            }
        }
        assertThat(jdbc().queryForObject("SELECT played_matches FROM playerstats", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc().queryForObject("SELECT won_legs FROM playerstats", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc().queryForObject("SELECT triple20s FROM playerstats", Integer.class))
                .isEqualTo(8);
    }

    @Test
    void winnerUpdatesEveryParticipantIncludingZeroTurnAverage() {
        String token = register("alice");
        register("bob");
        MatchResponse game = start(token, create(token, "301",
                List.of(new UserRequest("bob"))).gameId());
        // A saved leg close to checkout; Bob has not thrown yet.
        jdbc().update("UPDATE leg_player_stats SET points = 40 WHERE leg_id = ? "
                + "AND user_id = (SELECT user_id FROM users WHERE user_name = 'alice')",
                game.currentLegId());
        play(token, game, "alice", "20 2");
        var bob = jdbc().queryForMap("SELECT p.* FROM playerstats p JOIN users u "
                + "ON p.player_id = u.user_id WHERE u.user_name = 'bob'");
        assertThat(bob.get("played_matches")).isEqualTo(1);
        assertThat(bob.get("played_legs")).isEqualTo(1);
        assertThat(bob.get("won_matches")).isEqualTo(0);
        assertThat(((Number) bob.get("avg_points")).floatValue()).isZero();
    }

    @Test
    void unsupportedModesAndUnknownParticipantsCreateNothing() {
        String token = register("alice");
        assertThat(rest().postForEntity(GAMES_URL, authed(token,
                new CreateGameRequest("Cricket", List.of())), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest().postForEntity(GAMES_URL, authed(token,
                new CreateGameRequest("301", List.of(new UserRequest("ghost")))), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(countRows("matches")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"301", "501"})
    void firstToTwoRotatesStartersPreservesLegsAndAwardsMatchOnlyAtTarget(final String type) {
        String token = register("alice");
        register("bob");
        register("carol");
        var created = rest().postForEntity(GAMES_URL, authed(token,
                new CreateGameRequest(type, List.of(new UserRequest("bob"),
                        new UserRequest("carol")), 2)), MatchResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        MatchResponse game = start(token, created.getBody().gameId());
        assertThat(game.legsToWin()).isEqualTo(2);
        assertThat(game.format()).isEqualTo("FIRST_TO_LEGS");
        assertThat(game.turn()).isEqualTo("alice");
        MatchResponse previous = game;
        game = winLeg(token, game, "alice");
        assertThat(game.status()).isEqualTo(MatchStatus.IN_PROGRESS);
        assertThat(game.winner()).isNull();
        assertThat(game.finishedAt()).isNull();
        assertThat(game.legs()).hasSize(2);
        assertThat(game.currentLegId()).isPositive().isNotEqualTo(previous.currentLegId());
        assertThat(game.turn()).isEqualTo("bob");
        int starting = Integer.parseInt(type);
        assertThat(game.players()).allMatch(p -> p.points() == starting && p.turns() == 0);
        assertThat(game.legs().getFirst().winner()).isEqualTo("alice");
        assertThat(game.legs().getFirst().players().getFirst().points()).isZero();
        assertThat(jdbc().queryForObject("SELECT SUM(played_matches) FROM playerstats",
                Integer.class)).isZero();
        assertThat(round(token, previous, "alice", "20 2").getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(get(token, game.gameId())).isEqualTo(game);

        game = winLeg(token, game, "bob");
        assertThat(game.turn()).isEqualTo("carol");
        assertThat(game.legs()).hasSize(3);
        assertThat(game.status()).isEqualTo(MatchStatus.IN_PROGRESS);
        game = winLeg(token, game, "alice");
        assertThat(game.status()).isEqualTo(MatchStatus.COMPLETED);
        assertThat(game.winner()).isEqualTo("alice");
        assertThat(game.legs()).hasSize(3); // No extra leg after the target is reached.
        assertThat(game.players()).filteredOn(p -> p.username().equals("alice"))
                .extracting(MatchResponse.Player::legsWon).containsExactly(2L);
        var alice = jdbc().queryForMap("SELECT p.* FROM playerstats p JOIN users u "
                + "ON p.player_id = u.user_id WHERE u.user_name='alice'");
        assertThat(alice.get("won_legs")).isEqualTo(2);
        assertThat(alice.get("played_legs")).isEqualTo(3);
        assertThat(alice.get("won_matches")).isEqualTo(1);
        assertThat(alice.get("played_matches")).isEqualTo(1);
        var bob = jdbc().queryForMap("SELECT p.* FROM playerstats p JOIN users u "
                + "ON p.player_id = u.user_id WHERE u.user_name='bob'");
        assertThat(bob.get("won_legs")).isEqualTo(1);
        assertThat(bob.get("won_matches")).isEqualTo(0);
        assertThat(bob.get("played_matches")).isEqualTo(1);
        assertThat(get(token, game.gameId())).isEqualTo(game);
    }

    @Test
    void abandonedMultiLegMatchKeepsCompletedLegTotalsButAwardsNoMatch() {
        String token = register("alice");
        MatchResponse created = rest().postForObject(GAMES_URL,
                authed(token, new CreateGameRequest("501", List.of(), 3)), MatchResponse.class);
        MatchResponse game = winLeg(token, start(token, created.gameId()), "alice");
        assertThat(game.players().getFirst().legsWon()).isEqualTo(1);
        assertThat(game.players().getFirst().points()).isEqualTo(501);
        var ended = rest().postForObject(GAMES_URL + "/" + game.gameId() + "/abandon",
                authed(token, null), MatchResponse.class);
        assertThat(ended.status()).isEqualTo(MatchStatus.ABANDONED);
        assertThat(ended.legs()).extracting(MatchResponse.LegSummary::status)
                .containsExactly(MatchStatus.COMPLETED, MatchStatus.ABANDONED);
        var total = jdbc().queryForMap("SELECT * FROM playerstats");
        assertThat(total.get("won_legs")).isEqualTo(1);
        assertThat(total.get("played_legs")).isEqualTo(1);
        assertThat(total.get("won_matches")).isEqualTo(0);
        assertThat(total.get("played_matches")).isEqualTo(0);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 100})
    void invalidWinTargetsAreRejected(final int target) {
        String token = register("alice");
        var response = rest().postForEntity(GAMES_URL, authed(token,
                new CreateGameRequest("501", List.of(), target)), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(countRows("matches")).isZero();
    }

    @Test
    void concurrentLegCheckoutCreatesOnlyOneNextLeg() throws Exception {
        String token = register("alice");
        var created = rest().postForObject(GAMES_URL,
                authed(token, new CreateGameRequest("301", List.of(), 2)), MatchResponse.class);
        MatchResponse game = start(token, created.gameId());
        jdbc().update("UPDATE leg_player_stats SET points=40 WHERE leg_id=?", game.currentLegId());
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<HttpStatus> submit = () -> HttpStatus.valueOf(
                    round(token, game, "alice", "20 2").getStatusCode().value());
            var results = executor.invokeAll(List.of(submit, submit));
            assertThat(List.of(results.get(0).get(), results.get(1).get()))
                    .containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
        }
        var fresh = get(token, game.gameId());
        assertThat(fresh.legs()).hasSize(2);
        assertThat(fresh.currentLegId()).isNotEqualTo(game.currentLegId());
        assertThat(fresh.players().getFirst().legsWon()).isEqualTo(1);
        assertThat(fresh.players().getFirst().points()).isEqualTo(301);
        assertThat(jdbc().queryForObject("SELECT won_legs FROM playerstats", Integer.class))
                .isEqualTo(1);
    }

    private MatchResponse winLeg(final String token, final MatchResponse game,
                                 final String winner) {
        MatchResponse current = game;
        while (!winner.equals(current.turn())) {
            current = play(token, current, current.turn(), "");
        }
        // Seed the final checkout position; all other turn/progression rules use the real API.
        jdbc().update("UPDATE leg_player_stats SET points=40 WHERE leg_id=? "
                + "AND user_id=(SELECT user_id FROM users WHERE user_name=?)",
                current.currentLegId(), winner);
        return play(token, current, winner, "20 2");
    }

    private MatchResponse create(final String token, final String type,
                                 final List<UserRequest> players) {
        var response = rest().postForEntity(GAMES_URL,
                authed(token, new CreateGameRequest(type, players)), MatchResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private MatchResponse start(final String token, final long id) {
        var response = rest().postForEntity(GAMES_URL + "/" + id + "/start",
                authed(token, null), MatchResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private MatchResponse get(final String token, final long id) {
        return rest().exchange(GAMES_URL + "/" + id, HttpMethod.GET,
                new HttpEntity<>(bearer(token)), MatchResponse.class).getBody();
    }

    private ResponseEntity<String> round(final String token, final MatchResponse game,
                                                final String player, final String score) {
        return rest().postForEntity(GAMES_URL + "/" + game.gameId() + "/rounds",
                authed(token, new PlayedRoundRequest(player, score,
                        game.currentLegId(), game.revision())), String.class);
    }

    private MatchResponse play(final String token, final MatchResponse game,
                               final String player, final String score) {
        var response = rest().postForEntity(GAMES_URL + "/" + game.gameId() + "/rounds",
                authed(token, new PlayedRoundRequest(player, score,
                        game.currentLegId(), game.revision())), MatchResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }
}
