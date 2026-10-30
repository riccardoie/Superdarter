package lux.dartgame.integration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import lux.dartgame.dto.CreateGameRequest;
import lux.dartgame.dto.BoardHitResponse;
import lux.dartgame.dto.MatchResponse;
import lux.dartgame.dto.PlayedRoundRequest;
import lux.dartgame.dto.PlayerStatResponse;
import lux.dartgame.dto.UndoRoundRequest;
import lux.dartgame.dto.UserRequest;
import lux.dartgame.model.MatchStatus;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoardHitsIT extends AbstractIT {
    @Test
    void everyNumberIsPersistedImmediatelyAndMultipliersDoNotInflateHits() {
        String token = register("alice");
        MatchResponse game = create(token, "501", 1, List.of());
        assertThat(hits(token, "alice")).hasSize(21).allSatisfy((number, count) ->
                assertThat(count).isZero());
        for (int number = 1; number <= 20; number++) {
            game = play(token, game, number + " 1");
        }
        game = play(token, game, "25 1 25 2 0 1");
        Map<Integer, Long> all = hits(token, "alice");
        for (int number = 1; number <= 20; number++) assertThat(all.get(number)).isEqualTo(1L);
        assertThat(all).containsEntry(25, 2L).doesNotContainKeys(0, 21, 22, 23, 24);
        game = play(token, game, "20 1 20 2 20 3");
        assertThat(game.status()).isEqualTo(MatchStatus.IN_PROGRESS);
        assertThat(hits(token, "alice")).containsEntry(20, 4L).containsEntry(25, 2L);
        assertThat(detailedHits(token, "alice"))
                .containsEntry(20, new BoardHitResponse(4, 2, 1, 1))
                .containsEntry(25, new BoardHitResponse(2, 1, 1, 0));
        assertThat(jdbc().queryForObject("SELECT SUM(single_hits+double_hits+triple_hits) FROM round_board_hits",
                Long.class)).isEqualTo(25L);
        assertThat(rest().exchange(GAMES_URL + "/" + game.gameId(), HttpMethod.GET,
                authed(token, null), MatchResponse.class).getBody()).isEqualTo(game);
        Map<Integer, Long> beforeMisses = hits(token, "alice");
        play(token, game, "");
        assertThat(hits(token, "alice")).isEqualTo(beforeMisses);
        assertThat(jdbc().queryForObject("SELECT SUM(single_hits+double_hits+triple_hits) FROM round_board_hits",
                Long.class)).isEqualTo(25L);
    }

    @Test
    void bustedTurnsRecordNoHitsAndCorrectedCheckoutCountsOnlyWinningDarts() {
        String token = register("alice");
        MatchResponse game = create(token, "301", 1, List.of());
        jdbc().update("UPDATE leg_player_stats SET points=40 WHERE leg_id=?", game.currentLegId());
        game = play(token, game, "20 1 20 3 25 2");
        assertThat(game.players().getFirst().points()).isEqualTo(40);
        assertThat(hits(token, "alice")).allSatisfy((number, count) -> assertThat(count).isZero());
        assertThat(countRows("round_board_hits")).isZero();
        game = undo(token, game);
        assertThat(hits(token, "alice")).containsEntry(20, 0L);
        assertThat(countRows("round_board_hits")).isZero();
        play(token, game, "20 2 25 2 19 3");
        assertThat(hits(token, "alice")).containsEntry(20, 1L)
                .containsEntry(25, 0L).containsEntry(19, 0L);
        assertThat(detailedHits(token, "alice")).containsEntry(20, new BoardHitResponse(1, 0, 1, 0));
    }

    @Test
    void checkoutUndoAndReplayRestoreCountersWithoutDuplicatingHits() {
        String token = register("alice");
        MatchResponse game = create(token, "301", 1, List.of());
        game = play(token, game, "20 3 20 3 20 3");
        game = play(token, game, "20 3 19 3 2 1");
        Map<Integer, Long> before = hits(token, "alice");
        game = play(token, game, "1 2 25 2 20 3");
        assertThat(game.status()).isEqualTo(MatchStatus.COMPLETED);
        Map<Integer, Long> completed = hits(token, "alice");
        assertThat(completed).containsEntry(1, 1L).containsEntry(20, 4L).containsEntry(25, 0L);
        game = undo(token, game);
        assertThat(hits(token, "alice")).isEqualTo(before);
        play(token, game, "0 1 1 2");
        assertThat(hits(token, "alice")).isEqualTo(completed);
    }

    @Test
    void countsAccumulateAcrossLegsAndRematchesWithoutCopyingOldCounters() {
        String token = register("alice");
        MatchResponse game = create(token, "301", 2, List.of());
        game = winLeg(token, game);
        assertThat(game.legs()).hasSize(2);
        assertThat(hits(token, "alice")).containsEntry(20, 4L);
        game = winLeg(token, game);
        assertThat(game.status()).isEqualTo(MatchStatus.COMPLETED);
        Map<Integer, Long> completed = hits(token, "alice");
        assertThat(completed).containsEntry(20, 8L).containsEntry(1, 2L);
        MatchResponse rematch = rest().postForObject(GAMES_URL + "/" + game.gameId() + "/rematch",
                authed(token, null), MatchResponse.class);
        assertThat(hits(token, "alice")).isEqualTo(completed);
        rematch = play(token, rematch, "25 1");
        assertThat(hits(token, "alice")).containsEntry(20, 8L).containsEntry(25, 1L);
        undo(token, rematch);
        assertThat(hits(token, "alice")).isEqualTo(completed);
    }

    @Test
    void guestHitsRemainLocalAndDoNotLeakIntoAnAccountWithTheSameName() {
        String alice = register("alice");
        String bob = register("bob");
        MatchResponse game = create(alice, "501", 1, List.of("bob"));
        game = play(alice, game, "20 3");
        game = play(alice, game, "25 2 19 3");
        assertThat(hits(alice, "alice")).containsEntry(20, 1L)
                .containsEntry(25, 0L).containsEntry(19, 0L);
        assertThat(hits(bob, "bob")).allSatisfy((number, count) -> assertThat(count).isZero());
        assertThat(jdbc().queryForObject("SELECT SUM(single_hits+double_hits+triple_hits) FROM round_board_hits",
                Long.class)).isEqualTo(3L);
        undo(alice, game);
        assertThat(jdbc().queryForObject("SELECT SUM(single_hits+double_hits+triple_hits) FROM round_board_hits",
                Long.class)).isEqualTo(1L);
    }

    @Test
    void invalidAndStaleRoundsCannotChangeHitCounters() {
        String token = register("alice");
        MatchResponse initial = create(token, "501", 1, List.of());
        assertThat(round(token, initial, "21 1")).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(countRows("round_board_hits")).isZero();
        MatchResponse game = play(token, initial, "20 3");
        assertThat(round(token, initial, "25 2")).isEqualTo(HttpStatus.CONFLICT);
        assertThat(hits(token, "alice")).containsEntry(20, 1L).containsEntry(25, 0L);
        undo(token, game);
        assertThat(countRows("round_board_hits")).isZero();
    }

    @Test
    void concurrentRoundsAcrossGamesCannotLoseUserHits() throws Exception {
        String token = register("alice");
        MatchResponse first = create(token, "501", 1, List.of());
        MatchResponse second = create(token, "501", 1, List.of());
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<HttpStatus> left = () -> round(token, first, "20 3 25 1");
            Callable<HttpStatus> right = () -> round(token, second, "20 2 25 2");
            var results = executor.invokeAll(List.of(left, right));
            assertThat(results.get(0).get()).isEqualTo(HttpStatus.OK);
            assertThat(results.get(1).get()).isEqualTo(HttpStatus.OK);
        }
        assertThat(hits(token, "alice")).containsEntry(20, 2L).containsEntry(25, 2L);
        assertThat(detailedHits(token, "alice"))
                .containsEntry(20, new BoardHitResponse(2, 0, 1, 1))
                .containsEntry(25, new BoardHitResponse(2, 1, 1, 0));
    }

    @Test
    void abandonedRetainedGamesKeepHitsButErasedGamesRemoveTheirHits() {
        String token = register("alice");
        MatchResponse erased = play(token, create(token, "301", 1, List.of()), "20 3");
        rest().postForObject(GAMES_URL + "/" + erased.gameId() + "/abandon",
                authed(token, null), MatchResponse.class);
        assertThat(countRows("round_board_hits")).isZero();
        assertThat(hits(token, "alice")).containsEntry(20, 0L);
        MatchResponse retained = winLeg(token, create(token, "301", 2, List.of()));
        retained = play(token, retained, "25 1");
        rest().postForObject(GAMES_URL + "/" + retained.gameId() + "/abandon",
                authed(token, null), MatchResponse.class);
        assertThat(hits(token, "alice")).containsEntry(20, 4L).containsEntry(25, 1L);
    }

    @Test
    void databaseRejectsMissesInvalidNumbersAndInvalidCounts() {
        String token = register("alice");
        play(token, create(token, "501", 1, List.of()), "1 1");
        Long id = jdbc().queryForObject("SELECT round_id FROM played_rounds", Long.class);
        for (int number : List.of(0, -1, 21, 22, 23, 24, 26)) {
            assertThatThrownBy(() -> jdbc().update("INSERT INTO round_board_hits(round_id,board_number,single_hits) VALUES (?, ?, 1)",
                    id, number)).isInstanceOf(DataIntegrityViolationException.class);
        }
        for (int count : List.of(0, -1, 4)) {
            assertThatThrownBy(() -> jdbc().update("INSERT INTO round_board_hits(round_id,board_number,single_hits) VALUES (?, 20, ?)",
                    id, count)).isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> jdbc().update("INSERT INTO round_board_hits "
                + "(round_id,board_number,triple_hits) VALUES (?,25,1)", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc().update("INSERT INTO round_board_hits "
                + "(round_id,board_number,single_hits,double_hits,triple_hits) VALUES (?,20,1,2,1)", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private MatchResponse create(final String token, final String type, final int target,
                                  final List<String> guests) {
        MatchResponse game = rest().postForObject(GAMES_URL, authed(token,
                new CreateGameRequest(type, List.of(), target, guests)), MatchResponse.class);
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

    private HttpStatus round(final String token, final MatchResponse game, final String score) {
        return HttpStatus.valueOf(rest().postForEntity(GAMES_URL + "/" + game.gameId() + "/rounds",
                authed(token, new PlayedRoundRequest(game.turn(), score, game.currentLegId(),
                        game.revision())), String.class).getStatusCode().value());
    }

    private MatchResponse undo(final String token, final MatchResponse game) {
        return rest().postForObject(GAMES_URL + "/" + game.gameId() + "/undo",
                authed(token, new UndoRoundRequest(game.revision())), MatchResponse.class);
    }

    private MatchResponse winLeg(final String token, final MatchResponse game) {
        MatchResponse current = play(token, game, "20 3 20 3 20 3");
        current = play(token, current, "20 3 19 3 2 1");
        return play(token, current, "1 2");
    }

    private Map<Integer, Long> hits(final String token, final String username) {
        return detailedHits(token, username).entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue().total()));
    }

    private Map<Integer, BoardHitResponse> detailedHits(final String token, final String username) {
        return rest().postForObject(PLAYER_STATS_URL,
                authed(token, List.of(new UserRequest(username))), PlayerStatResponse[].class)[0]
                .boardHits();
    }
}
