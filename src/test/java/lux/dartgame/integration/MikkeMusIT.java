package lux.dartgame.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lux.dartgame.dto.BullOffDecisionRequest;
import lux.dartgame.dto.CreateGameRequest;
import lux.dartgame.dto.MatchResponse;
import lux.dartgame.dto.PlayedRoundRequest;
import lux.dartgame.dto.PlayerStatResponse;
import lux.dartgame.dto.UndoRoundRequest;
import lux.dartgame.dto.UserRequest;
import lux.dartgame.model.MatchStatus;
import lux.dartgame.model.MikkeMusState;
import lux.dartgame.service.MikkeMusRules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class MikkeMusIT extends AbstractIT {
    @Autowired private ObjectMapper mapper;

    @ParameterizedTest
    @ValueSource(strings = {"Mikke Mus Forced", "Mikke Mus Relaxed"})
    void soloCanCompleteAWholeGameWithExplicitAllocations(final String mode) {
        String token = register("alice");
        MatchResponse game = create(token, mode, 1, List.of());
        game = play(token, game, "20 2 20 2 20 2", List.of("DOUBLE", "DOUBLE", "DOUBLE"));
        game = play(token, game, "20 3 20 3 20 3", List.of("TREBLE", "TREBLE", "TREBLE"));
        game = play(token, game, "20 3 19 3 18 3", null);
        game = play(token, game, "17 3 16 3 15 3", null);
        game = play(token, game, "14 3 25 2 25 1", null);
        assertThat(game.status()).isEqualTo(MatchStatus.COMPLETED);
        assertThat(game.winner()).isEqualTo("alice");
        assertThat(game.players().getFirst().mikkeMarks()).allSatisfy((target, marks) ->
                assertThat(marks).isEqualTo(3));
        assertThat(stats(token).dartsThrown()).isEqualTo(15L);
        assertThat(stats(token).highestFirstNineAverage()).isNull();
        assertThat(stats(token).boardHits().get(20).total()).isEqualTo(7L);
        assertThat(get(token, game)).isEqualTo(game);
    }

    @Test
    void allocationsAndForcedProgressPersistAndUndoRestoresMarksAndHeatMap() {
        String token = register("alice");
        MatchResponse game = create(token, "Mikke Mus Forced", 1, List.of());
        assertThat(game.players().getFirst().mikkeMarks()).hasSize(10);
        game = play(token, game, "20 2 19 3 20 3", List.of("DOUBLE", "NUMBER", "NUMBER"));
        assertThat(game.players().getFirst().mikkeMarks()).containsEntry("D", 1)
                .containsEntry("20", 3).containsEntry("19", 0);
        assertThat(get(token, game)).isEqualTo(game);
        assertThat(game.players().getFirst().boardHits().get(20).total()).isEqualTo(2);
        game = undo(token, game);
        assertThat(game.players().getFirst().mikkeMarks()).allSatisfy((target, marks) ->
                assertThat(marks).isZero());
        assertThat(game.players().getFirst().boardHits().get(20).total()).isZero();
        assertThat(get(token, game)).isEqualTo(game);
        var invalid = rest().postForEntity(GAMES_URL + "/" + game.gameId() + "/rounds",
                authed(token, new PlayedRoundRequest(game.turn(), "20 1", game.currentLegId(),
                        game.revision(), List.of("TREBLE"))), String.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(get(token, game)).isEqualTo(game);
    }

    @Test
    void redemptionBullOffReplayManualDistanceDecisionAndUndoAreReloadable() throws Exception {
        String token = register("alice");
        String outsider = register("outsider");
        MatchResponse game = create(token, "Mikke Mus Relaxed", 1, List.of("Guest", "Too far"));
        var state = MikkeMusRules.initial(3);
        close(state, 0, 2);
        close(state, 1, 2);
        seed(game, state);
        game = play(token, get(token, game), "25 1", null);
        assertThat(game.status()).isEqualTo(MatchStatus.IN_PROGRESS);
        assertThat(game.mikkeMus().getPhase()).isEqualTo("REDEMPTION");
        assertThat(game.turn()).isEqualTo("Guest");
        assertThat(game.mikkeMus().getRedemption()).containsExactly(1);
        assertThat(get(token, game)).isEqualTo(game);
        game = play(token, game, "25 1", null);
        assertThat(game.mikkeMus().getPhase()).isEqualTo("BULL_OFF");
        assertThat(game.turn()).isEqualTo("alice");
        game = play(token, game, "25 1", null);
        MatchResponse beforeLastThrow = game;
        game = play(token, game, "25 2", null);
        assertThat(game.status()).isEqualTo(MatchStatus.COMPLETED);
        assertThat(game.winner()).isEqualTo("Guest");
        assertThat(stats(token).playedMatches()).isEqualTo(1);
        assertThat(stats(token).highestLegAverage()).isNull();
        game = undo(token, game);
        assertThat(game.mikkeMus()).isEqualTo(beforeLastThrow.mikkeMus());
        assertThat(game.turn()).isEqualTo("Guest");
        assertThat(stats(token).playedMatches()).isZero();
        game = play(token, game, "25 1", null);
        assertThat(game.mikkeMus().getPhase()).isEqualTo("BULL_OFF");
        assertThat(game.mikkeMus().getBullAttempt()).isEqualTo(2);
        assertThat(game.mikkeMus().getBullThrows()).isEmpty();
        assertThat(game.mikkeMus().getBullHistory()).hasSize(2);
        assertThat(get(token, game)).isEqualTo(game);
        game = undo(token, game);
        assertThat(game.mikkeMus()).isEqualTo(beforeLastThrow.mikkeMus());
        game = undo(token, game);
        game = play(token, game, "0 1", null);
        game = play(token, game, "0 1", null);
        assertThat(game.mikkeMus().getPhase()).isEqualTo("BULL_DECISION");
        assertThat(game.turn()).isNull();
        assertThat(get(token, game)).isEqualTo(game);
        var forbidden = rest().postForEntity(GAMES_URL + "/" + game.gameId() + "/bull-off",
                authed(outsider, new BullOffDecisionRequest(game.currentLegId(), game.revision(),
                        "Guest", false)), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var ineligible = rest().postForEntity(GAMES_URL + "/" + game.gameId() + "/bull-off",
                authed(token, new BullOffDecisionRequest(game.currentLegId(), game.revision(),
                        "Too far", false)), String.class);
        assertThat(ineligible.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        MatchResponse tied = game;
        game = decision(token, game, null, true);
        assertThat(game.mikkeMus().getBullAttempt()).isEqualTo(2);
        assertThat(game.mikkeMus().getBullHistory()).hasSize(2);
        game = undo(token, game);
        assertThat(game.mikkeMus()).isEqualTo(tied.mikkeMus());
        game = decision(token, game, "alice", false);
        assertThat(game.winner()).isEqualTo("alice");
        assertThat(stats(token).wonMatches()).isEqualTo(1);
        assertThat(stats(token).dartsThrown()).isEqualTo(2L); // Finish + physical bull-off only.
        assertThat(get(token, game)).isEqualTo(game);
        game = undo(token, game);
        assertThat(game.mikkeMus().getPhase()).isEqualTo("BULL_DECISION");
        assertThat(stats(token).wonMatches()).isZero();
        game = decision(token, game, "Guest", false);
        MatchResponse rematch = rest().postForObject(GAMES_URL + "/" + game.gameId() + "/rematch",
                authed(token, null), MatchResponse.class);
        assertThat(rematch.mikkeMus().getPhase()).isEqualTo("PLAY");
        assertThat(rematch.players().getFirst().mikkeMarks()).allSatisfy((target, marks) ->
                assertThat(marks).isZero());
    }

    @Test
    void newLegResetsMarksRotatesStarterAndCheckoutUndoRestoresThePreviousLeg() throws Exception {
        String token = register("alice");
        MatchResponse game = create(token, "Mikke Mus Forced", 2, List.of("Guest"));
        var state = MikkeMusRules.initial(2);
        close(state, 0, 2);
        seed(game, state);
        game = play(token, get(token, game), "25 1 20 3", null);
        assertThat(game.legs()).hasSize(2);
        assertThat(game.turn()).isEqualTo("Guest");
        assertThat(game.players().getFirst().mikkeMarks()).allSatisfy((target, marks) ->
                assertThat(marks).isZero());
        assertThat(game.legs().getFirst().players().getFirst().mikkeMarks()).containsEntry("B", 3);
        assertThat(stats(token).wonLegs()).isEqualTo(1);
        assertThat(game.legs().getFirst().players().getFirst().boardHits().get(20).total()).isZero();
        game = undo(token, game);
        assertThat(game.legs()).hasSize(1);
        assertThat(game.turn()).isEqualTo("alice");
        assertThat(game.players().getFirst().mikkeMarks()).containsEntry("B", 2);
        assertThat(stats(token).wonLegs()).isZero();
        assertThat(get(token, game)).isEqualTo(game);
    }

    private void close(final MikkeMusState state, final int position, final int bull) {
        state.getMarks().get(position).replaceAll((target, marks) -> 3);
        state.getMarks().get(position).put("B", bull);
    }

    private void seed(final MatchResponse game, final MikkeMusState state) throws Exception {
        jdbc().update("UPDATE legs SET mikke_state=?::jsonb WHERE leg_id=?",
                mapper.writeValueAsString(state), game.currentLegId());
    }

    private MatchResponse create(final String token, final String type, final int legs,
                                  final List<String> guests) {
        MatchResponse game = rest().postForObject(GAMES_URL,
                authed(token, new CreateGameRequest(type, List.of(), legs, guests)), MatchResponse.class);
        return rest().postForObject(GAMES_URL + "/" + game.gameId() + "/start",
                authed(token, null), MatchResponse.class);
    }

    private MatchResponse play(final String token, final MatchResponse game, final String score,
                                final List<String> allocations) {
        var response = rest().postForEntity(GAMES_URL + "/" + game.gameId() + "/rounds",
                authed(token, new PlayedRoundRequest(game.turn(), score, game.currentLegId(),
                        game.revision(), allocations)), MatchResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private MatchResponse decision(final String token, final MatchResponse game,
                                    final String winner, final boolean rethrow) {
        return rest().postForObject(GAMES_URL + "/" + game.gameId() + "/bull-off",
                authed(token, new BullOffDecisionRequest(game.currentLegId(), game.revision(),
                        winner, rethrow)), MatchResponse.class);
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
