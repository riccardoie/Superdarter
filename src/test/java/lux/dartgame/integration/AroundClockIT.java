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
import lux.dartgame.model.TargetGameState;
import lux.dartgame.service.AroundClockRules;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

class AroundClockIT extends AbstractIT {
    @Autowired private ObjectMapper mapper;

    @Test
    void fullSoloGamePersistsProgressHeatMapsAndUndoWithoutCountdownRecords() {
        String token = register("alice");
        MatchResponse game = create(token, 1, List.of());
        assertThat(game.players().getFirst().points()).isEqualTo(1);
        game = play(token, game, "1 3 4 3 7 3");
        assertThat(game.players().getFirst().points()).isEqualTo(10);
        assertThat(get(token, game)).isEqualTo(game);
        game = play(token, game, "10 3 13 3 16 3");
        assertThat(game.players().getFirst().points()).isEqualTo(19);
        game = play(token, game, "19 3 25 1 20 3");
        assertThat(game.status()).isEqualTo(MatchStatus.COMPLETED);
        assertThat(game.winner()).isEqualTo("alice");
        assertThat(game.players().getFirst().points()).isZero();
        assertThat(game.players().getFirst().boardHits().get(20).total()).isZero();
        var stats = stats(token);
        assertThat(stats.dartsThrown()).isEqualTo(8L);
        assertThat(stats.wonMatches()).isEqualTo(1);
        assertThat(stats.avgPoints()).isZero();
        assertThat(stats.highestLegAverage()).isNull();
        assertThat(stats.highestFirstNineAverage()).isNull();
        assertThat(stats.highestScore()).isZero();
        assertThat(get(token, game)).isEqualTo(game);
        game = undo(token, game);
        assertThat(game.players().getFirst().points()).isEqualTo(19);
        assertThat(game.aroundClock().getMarks().get(0)).containsEntry("NEXT", 19);
        assertThat(game.players().getFirst().boardHits().get(25).total()).isZero();
        assertThat(stats(token).wonMatches()).isZero();
        game = play(token, game, "19 2 25 2");
        MatchResponse rematch = rest().postForObject(GAMES_URL + "/" + game.gameId() + "/rematch",
                authed(token, null), MatchResponse.class);
        assertThat(rematch.gametype()).isEqualTo("Around the Clock");
        assertThat(rematch.players().getFirst().points()).isEqualTo(1);
        assertThat(rematch.aroundClock().getPhase()).isEqualTo("PLAY");
    }

    @Test
    void redemptionBullOffRethrowsAndDistanceDecisionsSurviveReloadAndUndo() throws Exception {
        String token = register("alice");
        MatchResponse game = create(token, 1, List.of("Guest", "Too far", "Last"));
        var state = AroundClockRules.initial(4);
        state.getMarks().get(0).put("NEXT", 21);
        state.getMarks().get(1).put("NEXT", 15);
        state.getMarks().get(2).put("NEXT", 14);
        state.getMarks().get(3).put("NEXT", 21);
        seed(game, state);
        game = play(token, get(token, game), "25 1");
        assertThat(game.aroundClock().getRedemption()).containsExactly(1, 3);
        assertThat(game.turn()).isEqualTo("Guest");
        game = play(token, game, "15 3 18 3 25 2");
        assertThat(game.turn()).isEqualTo("Last");
        game = play(token, game, "0 1");
        assertThat(game.aroundClock().getBullCandidates()).containsExactly(0, 1);
        game = play(token, game, "25 1");
        MatchResponse beforeRethrow = game;
        game = play(token, game, "25 1");
        assertThat(game.aroundClock().getPhase()).isEqualTo("BULL_OFF");
        assertThat(game.aroundClock().getBullAttempt()).isEqualTo(2);
        assertThat(get(token, game)).isEqualTo(game);
        game = undo(token, game);
        assertThat(game.aroundClock()).isEqualTo(beforeRethrow.aroundClock());
        game = play(token, game, "25 1");
        game = play(token, game, "0 1");
        game = play(token, game, "0 1");
        assertThat(game.aroundClock().getPhase()).isEqualTo("BULL_DECISION");
        assertThat(game.turn()).isNull();
        assertThat(get(token, game)).isEqualTo(game);
        MatchResponse tied = game;
        game = rest().postForObject(GAMES_URL + "/" + game.gameId() + "/bull-off",
                authed(token, new BullOffDecisionRequest(game.currentLegId(), game.revision(),
                        "Guest", false)), MatchResponse.class);
        assertThat(game.winner()).isEqualTo("Guest");
        game = undo(token, game);
        assertThat(game.aroundClock()).isEqualTo(tied.aroundClock());
        assertThat(game.status()).isEqualTo(MatchStatus.IN_PROGRESS);
    }

    @Test
    void nextLegResetsTargetsRotatesStarterAndUndoReopensThePreviousLeg() throws Exception {
        String token = register("alice");
        MatchResponse game = create(token, 2, List.of("Guest"));
        var state = AroundClockRules.initial(2);
        state.getMarks().get(0).put("NEXT", 21);
        seed(game, state);
        game = play(token, get(token, game), "25 2");
        assertThat(game.legs()).hasSize(2);
        assertThat(game.turn()).isEqualTo("Guest");
        assertThat(game.players()).allSatisfy(player -> assertThat(player.points()).isEqualTo(1));
        assertThat(game.legs().getFirst().players().getFirst().points()).isZero();
        game = undo(token, game);
        assertThat(game.legs()).hasSize(1);
        assertThat(game.players().getFirst().points()).isEqualTo(21);
        assertThat(game.turn()).isEqualTo("alice");
        assertThat(get(token, game)).isEqualTo(game);
    }

    private void seed(final MatchResponse game, final TargetGameState state) throws Exception {
        jdbc().update("UPDATE legs SET clock_state=?::jsonb WHERE leg_id=?",
                mapper.writeValueAsString(state), game.currentLegId());
        state.getMarks().forEach((position, progress) -> jdbc().update(
                "UPDATE leg_player_stats SET points=? WHERE leg_id=? AND position=?",
                progress.get("NEXT"), game.currentLegId(), position));
    }

    private MatchResponse create(final String token, final int legs, final List<String> guests) {
        MatchResponse game = rest().postForObject(GAMES_URL,
                authed(token, new CreateGameRequest("Around the Clock", List.of(), legs, guests)),
                MatchResponse.class);
        return rest().postForObject(GAMES_URL + "/" + game.gameId() + "/start",
                authed(token, null), MatchResponse.class);
    }

    private MatchResponse play(final String token, final MatchResponse game, final String score) {
        return rest().postForObject(GAMES_URL + "/" + game.gameId() + "/rounds",
                authed(token, new PlayedRoundRequest(game.turn(), score,
                        game.currentLegId(), game.revision())), MatchResponse.class);
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
