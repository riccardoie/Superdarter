package lux.dartgame.integration;

import java.util.List;
import java.util.stream.IntStream;
import lux.dartgame.dto.CreateGameRequest;
import lux.dartgame.dto.MatchResponse;
import lux.dartgame.dto.PlayedRoundRequest;
import lux.dartgame.dto.UndoRoundRequest;
import lux.dartgame.dto.UserRequest;
import lux.dartgame.model.MatchStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class GuestPlayersIT extends AbstractIT {
    @Test
    void guestCanWinReloadUndoAndReplayWithoutCreatingAnAccountOrProfile() {
        String token = register("alice");
        MatchResponse game = create(token, new CreateGameRequest("301 Single Out", List.of(),
                1, List.of(" Guest One ")));
        assertThat(game.players()).extracting(MatchResponse.Player::username)
                .containsExactly("alice", "Guest One");
        assertThat(game.players().getLast().guest()).isTrue();
        assertThat(game.players().getFirst().guest()).isFalse();
        game = start(token, game);
        game = win(token, game, "Guest One");
        assertThat(game.status()).isEqualTo(MatchStatus.COMPLETED);
        assertThat(game.winner()).isEqualTo("Guest One");
        assertThat(game.legs().getFirst().winner()).isEqualTo("Guest One");
        assertThat(get(token, game)).isEqualTo(game);
        assertThat(countRows("users")).isEqualTo(1);
        assertThat(countRows("playerstats")).isEqualTo(1);
        assertTotals("alice", 1, 0, 1, 0);
        game = undo(token, game);
        assertThat(game.winner()).isNull();
        assertThat(game.legs().getFirst().winner()).isNull();
        assertThat(game.turn()).isEqualTo("Guest One");
        assertTotals("alice", 0, 0, 0, 0);
        game = play(token, game, "1 1");
        assertThat(game.winner()).isEqualTo("Guest One");
        assertTotals("alice", 1, 0, 1, 0);
        assertThat(get(token, game)).isEqualTo(game);
    }

    @Test
    void guestsRotateAcrossLegsAndRegisteredPlayersStillReceiveTheirOwnResults() {
        String token = register("alice");
        register("carol");
        MatchResponse game = start(token, create(token, new CreateGameRequest("501", List.of(
                new UserRequest("carol")), 2, List.of("Guest One", "Guest Two"))));
        game = win(token, game, "Guest One");
        assertThat(game.turn()).isEqualTo("carol");
        assertThat(game.players()).allMatch(p -> p.points() == 501 && p.turns() == 0);
        game = win(token, game, "alice");
        assertThat(game.turn()).isEqualTo("Guest One");
        game = win(token, game, "Guest One");
        assertThat(game.winner()).isEqualTo("Guest One");
        assertThat(game.players()).filteredOn(MatchResponse.Player::guest)
                .extracting(MatchResponse.Player::legsWon).containsExactly(2L, 0L);
        assertTotals("alice", 3, 1, 1, 0);
        assertTotals("carol", 3, 0, 1, 0);
        game = undo(token, get(token, game));
        assertThat(game.status()).isEqualTo(MatchStatus.IN_PROGRESS);
        assertThat(game.players().getFirst().guest()).isTrue();
        assertTotals("alice", 2, 1, 0, 0);
        game = play(token, game, "20 2");
        assertThat(game.winner()).isEqualTo("Guest One");
        assertThat(countRows("users")).isEqualTo(2);
        assertThat(countRows("playerstats")).isEqualTo(2);
    }

    @Test
    void guestNameMatchingAnotherAccountGrantsNoAccessAndDoesNotAlterThatProfile() {
        String alice = register("alice");
        String bob = register("bob");
        MatchResponse game = start(alice, create(alice,
                new CreateGameRequest("301", List.of(), 1, List.of("bob"))));
        assertThat(rest().exchange(GAMES_URL + "/" + game.gameId(), HttpMethod.GET,
                authed(bob, null), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest().postForEntity(GAMES_URL + "/" + game.gameId() + "/start",
                authed(bob, null), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest().postForEntity(GAMES_URL + "/" + game.gameId() + "/rounds", authed(bob,
                new PlayedRoundRequest("alice", "20 3", game.currentLegId(), game.revision())),
                String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        game = win(alice, game, "bob");
        assertThat(game.winner()).isEqualTo("bob");
        assertTotals("bob", 0, 0, 0, 0);
        assertTotals("alice", 1, 0, 1, 0);
        MatchResponse[] bobsGames = rest().exchange(GAMES_URL, HttpMethod.GET,
                authed(bob, null), MatchResponse[].class).getBody();
        assertThat(bobsGames).isEmpty();
        assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM leg_player_stats s JOIN users u "
                + "ON s.user_id=u.user_id WHERE u.user_name='bob'", Integer.class)).isZero();
    }

    @Test
    void registeredWinnerAndAbandonmentHandleGuestsWithoutLeakingGuestHitsIntoTotals() {
        String token = register("alice");
        MatchResponse game = start(token, create(token,
                new CreateGameRequest("301", List.of(), 2, List.of("Guest"))));
        game = play(token, game, "");
        game = play(token, game, "20 3 20 3 20 3");
        game = win(token, game, "alice");
        assertTotals("alice", 1, 1, 0, 0);
        assertThat(jdbc().queryForObject("SELECT triple20s FROM playerstats", Integer.class)).isZero();
        game = rest().postForObject(GAMES_URL + "/" + game.gameId() + "/abandon",
                authed(token, null), MatchResponse.class);
        assertThat(game.status()).isEqualTo(MatchStatus.ABANDONED);
        assertThat(get(token, game)).isEqualTo(game);
        assertTotals("alice", 1, 1, 0, 0);
        MatchResponse soloGuest = start(token, create(token,
                new CreateGameRequest("301", List.of(), 1, List.of("Guest"))));
        soloGuest = win(token, soloGuest, "alice");
        assertThat(soloGuest.winner()).isEqualTo("alice");
        assertTotals("alice", 2, 2, 1, 1);
    }

    @Test
    void rejectsInvalidOrAmbiguousGuestNamesAndEnforcesCombinedPlayerLimit() {
        String token = register("alice");
        for (List<String> guests : List.of(List.of(""), List.of("   "), List.of("alice"),
                List.of("Sam", " Sam "), List.of("x".repeat(256)),
                IntStream.range(0, 10).mapToObj(i -> "guest" + i).toList())) {
            var response = rest().postForEntity(GAMES_URL, authed(token,
                    new CreateGameRequest("301", List.of(), 1, guests)), String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(countRows("matches")).isZero();
        }
        register("bob");
        assertThat(rest().postForEntity(GAMES_URL, authed(token,
                new CreateGameRequest("301", List.of(new UserRequest("bob")), 1, List.of("bob"))),
                String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rest().postForEntity(GAMES_URL, authed(token,
                new CreateGameRequest("301", List.of(), 1, java.util.Arrays.asList((String) null))),
                String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        MatchResponse max = create(token, new CreateGameRequest("301", List.of(), 1,
                IntStream.range(0, 9).mapToObj(i -> "guest" + i).toList()));
        assertThat(max.players()).hasSize(10);
        rest().exchange(GAMES_URL + "/" + max.gameId(), HttpMethod.DELETE, authed(token, null), Void.class);
        assertThat(countRows("match_participants")).isZero();
        assertThat(countRows("leg_player_stats")).isZero();
    }

    private MatchResponse create(final String token, final CreateGameRequest request) {
        var response = rest().postForEntity(GAMES_URL, authed(token, request), MatchResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private MatchResponse start(final String token, final MatchResponse game) {
        return rest().postForObject(GAMES_URL + "/" + game.gameId() + "/start",
                authed(token, null), MatchResponse.class);
    }

    private MatchResponse get(final String token, final MatchResponse game) {
        return rest().exchange(GAMES_URL + "/" + game.gameId(), HttpMethod.GET,
                authed(token, null), MatchResponse.class).getBody();
    }

    private MatchResponse play(final String token, final MatchResponse game, final String score) {
        var response = rest().postForEntity(GAMES_URL + "/" + game.gameId() + "/rounds",
                authed(token, new PlayedRoundRequest(game.turn(), score, game.currentLegId(),
                        game.revision())), MatchResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private MatchResponse undo(final String token, final MatchResponse game) {
        var response = rest().postForEntity(GAMES_URL + "/" + game.gameId() + "/undo",
                authed(token, new UndoRoundRequest(game.revision())), MatchResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private MatchResponse win(final String token, final MatchResponse game, final String name) {
        MatchResponse current = game;
        while (!name.equals(current.turn())) {
            current = play(token, current, "");
        }
        boolean singleOut = game.gametype().endsWith("Single Out");
        jdbc().update("UPDATE leg_player_stats SET points=? WHERE leg_id=? AND "
                + "(guest_name=? OR user_id=(SELECT user_id FROM users WHERE user_name=?))",
                singleOut ? 1 : 40, current.currentLegId(), name, name);
        return play(token, current, singleOut ? "1 1" : "20 2");
    }

    private void assertTotals(final String name, final int playedLegs, final int wonLegs,
                              final int playedMatches, final int wonMatches) {
        var totals = jdbc().queryForMap("SELECT p.* FROM playerstats p JOIN users u "
                + "ON p.player_id=u.user_id WHERE u.user_name=?", name);
        assertThat(totals.get("played_legs")).isEqualTo(playedLegs);
        assertThat(totals.get("won_legs")).isEqualTo(wonLegs);
        assertThat(totals.get("played_matches")).isEqualTo(playedMatches);
        assertThat(totals.get("won_matches")).isEqualTo(wonMatches);
    }
}
