package lux.dartgame.integration;

import java.util.List;
import lux.dartgame.dto.CreateGameRequest;
import lux.dartgame.dto.MatchResponse;
import lux.dartgame.dto.PlayedRoundRequest;
import lux.dartgame.dto.PlayerStatResponse;
import lux.dartgame.dto.UndoRoundRequest;
import lux.dartgame.dto.UserRequest;
import lux.dartgame.model.MatchStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class CricketIT extends AbstractIT {
    @Test
    void marksPointsAndOpponentClosuresPersistAndUndoRestoresOnlyTheThrower() {
        String token = register("alice");
        register("bob");
        befriend("alice", "bob");
        MatchResponse game = create(token, "Cricket", 1, List.of(new UserRequest("bob")),
                List.of("Guest"));
        assertThat(game.startingPoints()).isZero();
        assertThat(game.players()).allSatisfy(player -> {
            assertThat(player.points()).isZero();
            assertThat(player.cricketMarks()).hasSize(7).allSatisfy((number, count) ->
                    assertThat(count).isZero());
        });
        game = play(token, game, "20 3 20 2 25 2");
        assertThat(game.players().getFirst().points()).isEqualTo(40);
        assertThat(game.players().getFirst().cricketMarks()).containsEntry(20, 3).containsEntry(25, 2);
        game = play(token, game, "20 3 25 1 1 3");
        game = play(token, game, "20 3 25 2 25 2");
        assertThat(game.players().getLast().points()).isEqualTo(25);
        game = play(token, game, "20 3 25 2");
        assertThat(game.players().getFirst().points()).isEqualTo(65);
        assertThat(game.players().getFirst().cricketMarks()).containsEntry(25, 3);
        assertThat(get(token, game)).isEqualTo(game);
        game = undo(token, game);
        assertThat(game.players().getFirst().points()).isEqualTo(40);
        assertThat(game.players().getFirst().cricketMarks()).containsEntry(25, 2);
        assertThat(game.players().getLast().points()).isEqualTo(25);
        assertThat(game.turn()).isEqualTo("alice");
        assertThat(get(token, game)).isEqualTo(game);
    }

    @Test
    void soloWinningDartStopsTheRoundAndUndoRematchAndProfileStatsWork() {
        String token = register("alice");
        MatchResponse game = winLeg(token, create(token, "Cricket", 1, List.of(), List.of()));
        assertThat(game.status()).isEqualTo(MatchStatus.COMPLETED);
        assertThat(game.winner()).isEqualTo("alice");
        assertThat(game.players().getFirst().points()).isZero();
        assertThat(game.players().getFirst().scoringStats().dartsThrown()).isEqualTo(8);
        assertThat(get(token, game)).isEqualTo(game);
        PlayerStatResponse totals = stats(token);
        assertThat(totals.playedMatches()).isEqualTo(1);
        assertThat(totals.wonMatches()).isEqualTo(1);
        assertThat(totals.avgPoints()).isZero();
        assertThat(totals.highestCheckout()).isZero();
        assertThat(totals.boardHits().get(20).total()).isEqualTo(1);
        assertThat(totals.boardHits().get(25).total()).isEqualTo(2);
        game = undo(token, game);
        assertThat(game.status()).isEqualTo(MatchStatus.IN_PROGRESS);
        assertThat(game.players().getFirst().cricketMarks()).containsEntry(25, 0);
        assertThat(stats(token).playedLegs()).isZero();
        game = play(token, game, "25 2 25 1 20 3");
        assertThat(stats(token).boardHits().get(20).total()).isEqualTo(1);
        MatchResponse rematch = rest().postForObject(GAMES_URL + "/" + game.gameId() + "/rematch",
                authed(token, null), MatchResponse.class);
        assertThat(rematch.gametype()).isEqualTo("Cricket");
        assertThat(rematch.players().getFirst().cricketMarks()).allSatisfy((number, count) ->
                assertThat(count).isZero());
        assertThat(rematch.players().getFirst().points()).isZero();
    }

    @Test
    void multiLegCricketRotatesStartersResetsMarksAndSupportsCheckoutUndo() {
        String token = register("alice");
        MatchResponse game = create(token, "Cricket", 2, List.of(), List.of("Guest"));
        game = winLeg(token, game);
        assertThat(game.turn()).isEqualTo("Guest");
        assertThat(game.legs()).hasSize(2);
        assertThat(game.players()).allSatisfy(player ->
                assertThat(player.cricketMarks()).allSatisfy((number, count) -> assertThat(count).isZero()));
        game = undo(token, game);
        assertThat(game.legs()).hasSize(1);
        assertThat(game.turn()).isEqualTo("alice");
        game = play(token, game, "25 2 25 1");
        assertThat(game.turn()).isEqualTo("Guest");
        game = winLeg(token, game);
        assertThat(game.turn()).isEqualTo("alice");
        game = winLeg(token, game);
        assertThat(game.winner()).isEqualTo("alice");
        assertThat(game.legs()).hasSize(3);
        assertThat(stats(token).playedLegs()).isEqualTo(3);
        assertThat(stats(token).wonLegs()).isEqualTo(2);
    }

    @Test
    void invalidAndStaleRoundsLeaveMarksAndHitCountsUnchanged() {
        String token = register("alice");
        MatchResponse game = create(token, "Cricket", 1, List.of(), List.of());
        var invalid = rest().postForEntity(GAMES_URL + "/" + game.gameId() + "/rounds",
                authed(token, new PlayedRoundRequest(game.turn(), "25 3", game.currentLegId(),
                        game.revision())), String.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(get(token, game)).isEqualTo(game);
        MatchResponse fresh = play(token, game, "20 3");
        var stale = rest().postForEntity(GAMES_URL + "/" + game.gameId() + "/rounds",
                authed(token, new PlayedRoundRequest(game.turn(), "19 3", game.currentLegId(),
                        game.revision())), String.class);
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(get(token, fresh)).isEqualTo(fresh);
        assertThat(stats(token).boardHits().get(19).total()).isZero();
    }

    @Test
    void cricketDoesNotDiluteCountdownAveragesOrCheckoutBestsIncludingAfterUndo() {
        String token = register("alice");
        MatchResponse countdown = create(token, "301", 1, List.of(), List.of());
        countdown = play(token, countdown, "20 3 20 3 20 3");
        countdown = play(token, countdown, "20 3 19 3 2 1");
        play(token, countdown, "1 2");
        float average = stats(token).avgPoints();
        MatchResponse cricket = winLeg(token, create(token, "Cricket", 1, List.of(), List.of()));
        assertThat(stats(token).avgPoints()).isEqualTo(average);
        countdown = create(token, "301", 1, List.of(), List.of());
        countdown = play(token, countdown, "20 3 20 3 20 3");
        countdown = play(token, countdown, "20 3 19 3 2 1");
        play(token, countdown, "1 2");
        assertThat(stats(token).avgPoints()).isEqualTo(average);
        undo(token, cricket);
        assertThat(stats(token).avgPoints()).isEqualTo(average);
        assertThat(stats(token).highestCheckout()).isEqualTo(2);
        assertThat(stats(token).playedMatches()).isEqualTo(2);
    }

    private MatchResponse create(final String token, final String type, final int legs,
                                  final List<UserRequest> players, final List<String> guests) {
        MatchResponse game = rest().postForObject(GAMES_URL,
                authed(token, new CreateGameRequest(type, players, legs, guests)), MatchResponse.class);
        return rest().postForObject(GAMES_URL + "/" + game.gameId() + "/start",
                authed(token, null), MatchResponse.class);
    }

    private MatchResponse play(final String token, final MatchResponse game, final String score) {
        var response = rest().postForEntity(GAMES_URL + "/" + game.gameId() + "/rounds",
                authed(token, new PlayedRoundRequest(game.turn(), score, game.currentLegId(),
                        game.revision())), MatchResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private MatchResponse winLeg(final String token, final MatchResponse game) {
        String winner = game.turn();
        MatchResponse current = game;
        for (String score : List.of("20 3 19 3 18 3", "17 3 16 3 15 3", "25 2 25 1 20 3")) {
            while (!winner.equals(current.turn())) {
                current = play(token, current, "");
            }
            current = play(token, current, score);
        }
        return current;
    }

    private MatchResponse get(final String token, final MatchResponse game) {
        return rest().exchange(GAMES_URL + "/" + game.gameId(), HttpMethod.GET,
                authed(token, null), MatchResponse.class).getBody();
    }

    private MatchResponse undo(final String token, final MatchResponse game) {
        return rest().postForObject(GAMES_URL + "/" + game.gameId() + "/undo",
                authed(token, new UndoRoundRequest(game.revision())), MatchResponse.class);
    }

    private PlayerStatResponse stats(final String token) {
        return rest().postForObject(PLAYER_STATS_URL,
                authed(token, List.of(new UserRequest("alice"))), PlayerStatResponse[].class)[0];
    }
}
