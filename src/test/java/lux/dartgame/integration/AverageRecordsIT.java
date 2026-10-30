package lux.dartgame.integration;

import java.util.List;
import lux.dartgame.dto.CreateGameRequest;
import lux.dartgame.dto.MatchResponse;
import lux.dartgame.dto.PlayedRoundRequest;
import lux.dartgame.dto.PlayerStatResponse;
import lux.dartgame.dto.UndoRoundRequest;
import lux.dartgame.dto.UserRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AverageRecordsIT extends AbstractIT {
    @Test
    void completionRecordsBestsAndUndoFallsBackToEarlierGamesAndLegs() {
        String token = register("alice");
        assertThat(stats(token).highestLegAverage()).isNull();
        assertThat(stats(token).highestGameAverage()).isNull();
        assertThat(stats(token).highestFirstNineAverage()).isNull();
        MatchResponse baseline = create(token, "301", 1);
        baseline = play(token, baseline, "20 3 20 3 20 3");
        baseline = play(token, baseline, "20 3 19 3 2 1");
        assertThat(stats(token).highestFirstNineAverage()).isNull();
        play(token, baseline, "1 2");
        assertThat(stats(token).highestLegAverage()).isCloseTo(301f / 3, within(0.001f));
        assertThat(stats(token).highestGameAverage()).isCloseTo(301f / 3, within(0.001f));
        assertThat(stats(token).highestFirstNineAverage()).isEqualTo(129f);

        MatchResponse game = create(token, "301 Single Out", 2);
        game = play(token, game, "20 3 20 3 20 3");
        game = play(token, game, "20 3 20 3 1 1");
        assertThat(stats(token).highestLegAverage()).isEqualTo(150.5f);
        assertThat(stats(token).highestFirstNineAverage()).isEqualTo(150.5f);
        assertThat(stats(token).highestGameAverage()).isCloseTo(301f / 3, within(0.001f));
        game = play(token, game, "");
        game = play(token, game, "20 3 20 3 20 3");
        game = play(token, game, "20 3 20 3 1 1");
        PlayerStatResponse records = stats(token);
        assertThat(records.highestGameAverage()).isCloseTo(120.4f, within(0.001f));
        assertThat(records.highestLegAverage()).isEqualTo(150.5f);
        assertThat(rest().exchange(GAMES_URL + "/" + game.gameId(), HttpMethod.GET,
                authed(token, null), MatchResponse.class).getBody()).isEqualTo(game);
        game = undo(token, game);
        assertThat(stats(token).highestGameAverage()).isCloseTo(301f / 3, within(0.001f));
        assertThat(stats(token).highestLegAverage()).isEqualTo(150.5f);
        game = play(token, game, "20 3 20 3 1 1");
        assertThat(stats(token).highestGameAverage()).isEqualTo(records.highestGameAverage());
        game = undo(token, game);
        game = undo(token, game);
        game = undo(token, game);
        undo(token, game);
        assertThat(stats(token).highestLegAverage()).isCloseTo(301f / 3, within(0.001f));
        assertThat(stats(token).highestFirstNineAverage()).isEqualTo(129f);
    }

    @Test
    void unknownOpeningDartsDoNotInventAFirstNineBest() {
        String token = register("alice");
        MatchResponse game = create(token, "301", 1);
        game = play(token, game, "20 3 20 3 20 3");
        game = play(token, game, "20 3 19 3 2 1");
        jdbc().update("UPDATE leg_player_stats SET scoring_stats_available=false WHERE leg_id=?",
                game.currentLegId());
        game = play(token, game, "1 2");
        assertThat(stats(token).highestFirstNineAverage()).isNull();
        assertThat(stats(token).highestLegAverage()).isCloseTo(301f / 3, within(0.001f));
        undo(token, game);
        assertThat(stats(token).highestLegAverage()).isNull();
        assertThat(stats(token).highestGameAverage()).isNull();
    }

    @Test
    void cricketWinsNeverCreateCountdownAverageRecords() {
        String token = register("alice");
        MatchResponse game = create(token, "Cricket", 1);
        game = play(token, game, "20 3 19 3 18 3");
        game = play(token, game, "17 3 16 3 15 3");
        play(token, game, "25 2 25 1");
        PlayerStatResponse result = stats(token);
        assertThat(result.wonMatches()).isEqualTo(1);
        assertThat(result.highestLegAverage()).isNull();
        assertThat(result.highestGameAverage()).isNull();
        assertThat(result.highestFirstNineAverage()).isNull();
    }

    private MatchResponse create(final String token, final String type, final int legs) {
        MatchResponse game = rest().postForObject(GAMES_URL,
                authed(token, new CreateGameRequest(type, List.of(), legs)), MatchResponse.class);
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

    private MatchResponse undo(final String token, final MatchResponse game) {
        return rest().postForObject(GAMES_URL + "/" + game.gameId() + "/undo",
                authed(token, new UndoRoundRequest(game.revision())), MatchResponse.class);
    }

    private PlayerStatResponse stats(final String token) {
        return rest().postForObject(PLAYER_STATS_URL,
                authed(token, List.of(new UserRequest("alice"))), PlayerStatResponse[].class)[0];
    }
}
