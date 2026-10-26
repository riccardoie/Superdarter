package lux.dartgame.service;

import lux.dartgame.dto.GameResponse;
import lux.dartgame.dto.PlayedRoundRequest;
import lux.dartgame.exception.AccessDeniedException;
import lux.dartgame.exception.GameStatNotFoundException;
import lux.dartgame.exception.InvalidScoreException;
import lux.dartgame.exception.InvalidTurnException;
import lux.dartgame.exception.NoAvailableGameException;
import lux.dartgame.exception.SessionNotFoundException;
import lux.dartgame.exception.UsernameNotFoundException;
import lux.dartgame.model.Game;
import lux.dartgame.model.GameStat;
import lux.dartgame.model.GameStatsId;
import lux.dartgame.model.Gametype;
import lux.dartgame.model.Session;
import lux.dartgame.model.User;
import lux.dartgame.repository.GameRepository;
import lux.dartgame.repository.GameStatRepository;
import lux.dartgame.repository.SessionRepository;
import lux.dartgame.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    private static final long SESSION_ID = 5L;
    private static final long GAME_ID = 10L;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GameStatRepository gameStatRepository;

    @InjectMocks
    private GameService gameService;

    private User user(final long id, final String name) {
        User user = new User();
        user.setUserId(id);
        user.setUserName(name);
        return user;
    }

    private Gametype gametype(final String name) {
        Gametype gametype = new Gametype();
        gametype.setGametype(name);
        return gametype;
    }

    private Game game(final long id, final Gametype gametype, final User winner) {
        Game game = new Game();
        game.setGameId(id);
        game.setGametype(gametype);
        game.setWinner(winner);
        return game;
    }

    private GameStat stat(final User user, final Game game, final int points,
                          final int turn, final int position) {
        GameStat stat = new GameStat();
        stat.setGameStatsId(new GameStatsId(user.getUserId(), game.getGameId()));
        stat.setUser(user);
        stat.setGame(game);
        stat.setPoints(points);
        stat.setTurn(turn);
        stat.setPosition(position);
        game.getGameStats().add(stat);
        return stat;
    }

    private Session session(final long id, final User owner, final Game... games) {
        Session session = new Session();
        session.setSessionId(id);
        session.setOwner(owner);
        session.addPlayers(owner);
        for (Game game : games) {
            session.addGame(game);
        }
        return session;
    }

    private Session session(final long id, final User owner, final Set<User> players,
                            final Game... games) {
        Session session = session(id, owner, games);
        session.setPlayers(new HashSet<>(players));
        session.addPlayers(owner);
        return session;
    }

    @Test
    void startGame_returnsNextPlayerOfAvailableGame() {
        User alice = user(1L, "alice");
        User bob = user(2L, "bob");
        Game game = game(GAME_ID, gametype("301"), null);
        stat(alice, game, 100, 0, 0);
        stat(bob, game, 50, 0, 1);
        Session session = session(SESSION_ID, alice, game);

        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(userRepository.findUserNameById(1L)).thenReturn(Optional.of("alice"));

        GameResponse response = gameService.startGame("301", SESSION_ID, "alice");

        assertThat(response.gametype()).isEqualTo("301");
        assertThat(response.turn()).isEqualTo("alice");
        assertThat(response.gameId()).isEqualTo(GAME_ID);
        assertThat(response.isFinished()).isFalse();
    }

    @Test
    void startGame_notMemberOfSession_throwsAccessDenied() {
        User carol = user(3L, "carol");
        User bob = user(2L, "bob");
        Game game = game(GAME_ID, gametype("301"), null);
        stat(bob, game, 50, 0, 0);
        Session session = session(SESSION_ID, carol, Set.of(bob), game);

        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> gameService.startGame("301", SESSION_ID, "alice"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void startGame_noAvailableGame_thrown() {
        User alice = user(1L, "alice");
        User carol = user(3L, "carol");
        Game game = game(GAME_ID, gametype("301"), carol);
        stat(carol, game, 150, 0, 0);
        Session session = session(SESSION_ID, alice, game);

        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> gameService.startGame("301", SESSION_ID, "alice"))
                .isInstanceOf(NoAvailableGameException.class);
    }

    @Test
    void startGame_gametypeDoesNotMatchAnyGame_throws() {
        User alice = user(1L, "alice");
        Game game = game(GAME_ID, gametype("501"), null);
        stat(alice, game, 100, 0, 0);
        Session session = session(SESSION_ID, alice, game);

        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> gameService.startGame("301", SESSION_ID, "alice"))
                .isInstanceOf(NoAvailableGameException.class);
    }

    @Test
    void startGame_gameWithoutStats_throwsNoAvailableGame() {
        User alice = user(1L, "alice");
        Game game = game(GAME_ID, gametype("301"), null);
        Session session = session(SESSION_ID, alice, game);

        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> gameService.startGame("301", SESSION_ID, "alice"))
                .isInstanceOf(NoAvailableGameException.class);
    }

    @Test
    void startGame_unknownSession_throws() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameService.startGame("301", SESSION_ID, "alice"))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void playRound_unknownUsername_throws() {
        when(userRepository.findByUserName("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameService.playRound(
                new PlayedRoundRequest("ghost", "20 3", GAME_ID), "ghost", SESSION_ID))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void playRound_unknownGame_throws() {
        User alice = user(1L, "alice");
        when(userRepository.findByUserName("alice")).thenReturn(Optional.of(alice));
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameService.playRound(
                new PlayedRoundRequest("alice", "20 3", GAME_ID), "alice", SESSION_ID))
                .isInstanceOf(NoAvailableGameException.class);
    }

    @Test
    void playRound_unknownSession_throws() {
        User alice = user(1L, "alice");
        Game game = game(GAME_ID, gametype("301"), null);

        when(userRepository.findByUserName("alice")).thenReturn(Optional.of(alice));
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameService.playRound(
                new PlayedRoundRequest("alice", "20 3", GAME_ID), "alice", SESSION_ID))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void playRound_notMemberOfSession_throwsAccessDenied() {
        User alice = user(1L, "alice");
        User carol = user(3L, "carol");
        User bob = user(2L, "bob");
        Game game = game(GAME_ID, gametype("301"), null);
        stat(bob, game, 50, 0, 0);
        Session session = session(SESSION_ID, carol, Set.of(bob), game);

        when(userRepository.findByUserName("alice")).thenReturn(Optional.of(alice));
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> gameService.playRound(
                new PlayedRoundRequest("alice", "20 3", GAME_ID), "alice", SESSION_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void playRound_gameAlreadyFinished_throws() {
        User alice = user(1L, "alice");
        User bob = user(2L, "bob");
        Game game = game(GAME_ID, gametype("301"), bob);
        Session session = session(SESSION_ID, alice, game);

        when(userRepository.findByUserName("alice")).thenReturn(Optional.of(alice));
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> gameService.playRound(
                new PlayedRoundRequest("alice", "20 3", GAME_ID), "alice", SESSION_ID))
                .isInstanceOf(InvalidTurnException.class);
    }

    @Test
    void playRound_outOfTurn_throws() {
        User alice = user(1L, "alice");
        User bob = user(2L, "bob");
        Game game = game(GAME_ID, gametype("301"), null);
        stat(bob, game, 50, 0, 0);
        stat(alice, game, 100, 1, 1);
        Session session = session(SESSION_ID, alice, game);

        when(userRepository.findByUserName("alice")).thenReturn(Optional.of(alice));
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> gameService.playRound(
                new PlayedRoundRequest("alice", "20 3", GAME_ID), "alice", SESSION_ID))
                .isInstanceOf(InvalidTurnException.class);
    }

    @Test
    void playRound_userWithoutStatInGame_throws() {
        User alice = user(1L, "alice");
        Game game = game(GAME_ID, gametype("301"), null);
        stat(alice, game, 100, 0, 0);
        Session session = session(SESSION_ID, alice, game);

        when(userRepository.findByUserName("alice")).thenReturn(Optional.of(alice));
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(gameStatRepository.findById(new GameStatsId(1L, GAME_ID)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameService.playRound(
                new PlayedRoundRequest("alice", "20 3", GAME_ID), "alice", SESSION_ID))
                .isInstanceOf(GameStatNotFoundException.class);
    }

    @Test
    void playRound_impossibleScore_throws() {
        User alice = user(1L, "alice");
        User bob = user(2L, "bob");
        Game game = game(GAME_ID, gametype("301"), null);
        GameStat aliceStat = stat(alice, game, 100, 0, 0);
        stat(bob, game, 50, 0, 1);
        Session session = session(SESSION_ID, alice, game);

        when(userRepository.findByUserName("alice")).thenReturn(Optional.of(alice));
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(gameStatRepository.findById(new GameStatsId(1L, GAME_ID)))
                .thenReturn(Optional.of(aliceStat));

        assertThatThrownBy(() -> gameService.playRound(
                new PlayedRoundRequest("alice", "20 3 20 3 20 1 20 1 3 1", GAME_ID),
                "alice", SESSION_ID))
                .isInstanceOf(InvalidScoreException.class);

        assertThat(aliceStat.getPoints()).isEqualTo(100);
        assertThat(aliceStat.getTurn()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"301", "501"})
    void playRound_validScore_updatesStatAndPassesTurn(final String gameType) {
        User alice = user(1L, "alice");
        User bob = user(2L, "bob");
        Game game = game(GAME_ID, gametype(gameType), null);
        int startingPoints = Integer.parseInt(gameType);
        GameStat aliceStat = stat(alice, game, startingPoints, 0, 0);
        stat(bob, game, startingPoints, 0, 1);
        Session session = session(SESSION_ID, alice, game);

        when(userRepository.findByUserName("alice")).thenReturn(Optional.of(alice));
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(gameStatRepository.findById(new GameStatsId(1L, GAME_ID)))
                .thenReturn(Optional.of(aliceStat));
        when(userRepository.getReferenceById(2L)).thenReturn(bob);

        GameResponse response = gameService.playRound(
                new PlayedRoundRequest("alice", "20 3", GAME_ID), "alice", SESSION_ID);

        assertThat(response.gametype()).isEqualTo(gameType);
        assertThat(response.turn()).isEqualTo("bob");
        assertThat(response.gameId()).isEqualTo(GAME_ID);
        assertThat(response.isFinished()).isFalse();
        assertThat(aliceStat.getPoints()).isEqualTo(startingPoints - 60);
        assertThat(aliceStat.getTurn()).isEqualTo(1);
        assertThat(aliceStat.getHighestScore()).isEqualTo(60);
    }

    @Test
    void playRound_exactScoreWins() {
        User alice = user(1L, "alice");
        User bob = user(2L, "bob");
        Game game = game(GAME_ID, gametype("301"), null);
        GameStat aliceStat = stat(alice, game, 60, 4, 0);
        stat(bob, game, 301, 4, 1);
        Session session = session(SESSION_ID, alice, game);

        when(userRepository.findByUserName("alice")).thenReturn(Optional.of(alice));
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(gameStatRepository.findById(new GameStatsId(1L, GAME_ID)))
                .thenReturn(Optional.of(aliceStat));

        GameResponse response = gameService.playRound(
                new PlayedRoundRequest("alice", "20 1 20 2", GAME_ID), "alice", SESSION_ID);

        assertThat(response.turn()).isEqualTo("alice");
        assertThat(response.isFinished()).isTrue();
        assertThat(game.getWinner()).isEqualTo(alice);
        assertThat(aliceStat.getPoints()).isZero();
        assertThat(aliceStat.getHighestCheckout()).isEqualTo(60);
        assertThat(alice.getStats().getAvgPoints()).isEqualTo(301f / 5);
        assertThat(bob.getStats().getAvgPoints()).isZero();
    }

    @Test
    void playRound_bustKeepsPointsAndPassesTurn() {
        User alice = user(1L, "alice");
        User bob = user(2L, "bob");
        Game game = game(GAME_ID, gametype("301"), null);
        GameStat aliceStat = stat(alice, game, 11, 0, 0);
        stat(bob, game, 50, 0, 1);
        Session session = session(SESSION_ID, alice, game);

        when(userRepository.findByUserName("alice")).thenReturn(Optional.of(alice));
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(gameStatRepository.findById(new GameStatsId(1L, GAME_ID)))
                .thenReturn(Optional.of(aliceStat));
        when(userRepository.getReferenceById(2L)).thenReturn(bob);

        GameResponse response = gameService.playRound(
                new PlayedRoundRequest("alice", "20 3", GAME_ID), "alice", SESSION_ID);

        assertThat(response.turn()).isEqualTo("bob");
        assertThat(response.isFinished()).isFalse();
        assertThat(aliceStat.getPoints()).isEqualTo(11);
        assertThat(aliceStat.getTurn()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"301", "501"})
    void getGameStats_includesStartingPointsWithAndWithoutPlayerFilter(final String gameType) {
        User alice = user(1L, "alice");
        Game game = game(GAME_ID, gametype(gameType), null);
        stat(alice, game, 41, 3, 0);
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));

        for (Optional<List<String>> players : List.of(Optional.<List<String>>empty(),
                Optional.of(List.of("alice")))) {
            var stats = gameService.getGameStats(GAME_ID, players);
            assertThat(stats).hasSize(1);
            assertThat(stats.get(0).startingPoints()).isEqualTo(Integer.parseInt(gameType));
            assertThat(stats.get(0).points()).isEqualTo(41);
        }
    }

    @Test
    void startGame_prefersPlayedGameEvenAfterZeroScoreTurn() {
        User alice = user(1L, "alice");
        Game unstarted = game(GAME_ID, gametype("501"), null);
        stat(alice, unstarted, 501, 0, 0);
        Game started = game(GAME_ID + 1, gametype("501"), null);
        stat(alice, started, 501, 1, 0);
        Session session = session(SESSION_ID, alice, unstarted, started);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(userRepository.findUserNameById(1L)).thenReturn(Optional.of("alice"));

        assertThat(gameService.startGame("501", SESSION_ID, "alice").gameId())
                .isEqualTo(started.getGameId());
    }

    @Test
    void deleteGame_onlyDeletesGameWithoutTurns() {
        User alice = user(1L, "alice");
        Game started = game(GAME_ID, gametype("501"), null);
        stat(alice, started, 501, 1, 0);
        Game unstarted = game(GAME_ID + 1, gametype("501"), null);
        stat(alice, unstarted, 501, 0, 0);
        Session session = session(SESSION_ID, alice, started, unstarted);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        gameService.deleteGame(SESSION_ID, "alice", "501");

        verify(gameRepository).delete(unstarted);
        assertThat(session.getGames()).containsExactly(started);
    }

    @ParameterizedTest
    @CsvSource({
        "40, '20 2', 0, true, 0, 0, 40",
        "50, '25 2', 0, true, 0, 1, 50",
        "40, '0 1 20 2', 0, true, 0, 0, 40",
        "40, '20 2 20 3 25 2', 0, true, 0, 0, 40",
        "20, '20 1', 20, false, 0, 0, 0",
        "60, '20 3', 60, false, 1, 0, 0",
        "25, '25 1', 25, false, 0, 1, 0",
        "21, '20 1', 21, false, 0, 0, 0",
        "40, '20 1 19 1', 40, false, 0, 0, 0",
        "40, '20 1 20 3 25 2', 40, false, 1, 0, 0",
        "40, '20 1 20 1', 40, false, 0, 0, 0",
        "100, '20 3 20 2', 0, true, 1, 0, 100",
        "501, '20 3 20 3 20 3', 321, false, 3, 0, 180",
        "501, '0 1', 501, false, 0, 0, 0"
    })
    void playRound_doubleOutRules(final int points, final String score,
                                 final int remaining, final boolean won,
                                 final int triples, final int bullseyes, final int highest) {
        User alice = user(1L, "alice");
        Game game = game(GAME_ID, gametype("501"), null);
        GameStat stat = stat(alice, game, points, 0, 0);
        Session session = session(SESSION_ID, alice, game);
        when(userRepository.findByUserName("alice")).thenReturn(Optional.of(alice));
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(gameStatRepository.findById(new GameStatsId(1L, GAME_ID)))
                .thenReturn(Optional.of(stat));
        if (!won) {
            when(userRepository.getReferenceById(1L)).thenReturn(alice);
        }

        GameResponse response = gameService.playRound(
                new PlayedRoundRequest("alice", score, GAME_ID), "alice", SESSION_ID);

        assertThat(response.isFinished()).isEqualTo(won);
        assertThat(stat.getPoints()).isEqualTo(remaining);
        assertThat(stat.getTurn()).isEqualTo(1);
        assertThat(stat.getTriple20s()).isEqualTo(triples);
        assertThat(stat.getBullseyes()).isEqualTo(bullseyes);
        assertThat(stat.getHighestScore()).isEqualTo(highest);
        assertThat(stat.getHighestCheckout()).isEqualTo(won ? points : 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"20 3 20 3 20 3 1 1", "-20 2", "20 -2", "0 2",
            "25 3", "21 1", "20", "abc", "20 2 garbage"})
    void playRound_invalidDartsDoNotChangeStats(final String score) {
        User alice = user(1L, "alice");
        Game game = game(GAME_ID, gametype("301"), null);
        GameStat stat = stat(alice, game, 301, 0, 0);
        Session session = session(SESSION_ID, alice, game);
        when(userRepository.findByUserName("alice")).thenReturn(Optional.of(alice));
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(gameStatRepository.findById(new GameStatsId(1L, GAME_ID)))
                .thenReturn(Optional.of(stat));

        assertThatThrownBy(() -> gameService.playRound(
                new PlayedRoundRequest("alice", score, GAME_ID), "alice", SESSION_ID))
                .isInstanceOf(InvalidScoreException.class);
        assertThat(stat.getPoints()).isEqualTo(301);
        assertThat(stat.getTurn()).isZero();
    }
}
