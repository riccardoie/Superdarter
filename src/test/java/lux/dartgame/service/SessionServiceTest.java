package lux.dartgame.service;

import lux.dartgame.dto.GameRequest;
import lux.dartgame.dto.GametypeResponse;
import lux.dartgame.dto.SessionResponse;
import lux.dartgame.dto.UserRequest;
import lux.dartgame.exception.AccessDeniedException;
import lux.dartgame.exception.GameModeNotFoundException;
import lux.dartgame.exception.NoSessionsForThisUserException;
import lux.dartgame.exception.SessionNotFoundException;
import lux.dartgame.exception.UsernameNotFoundException;
import lux.dartgame.model.Game;
import lux.dartgame.model.GameStat;
import lux.dartgame.model.Gametype;
import lux.dartgame.model.Session;
import lux.dartgame.model.User;
import lux.dartgame.repository.GametypeRepository;
import lux.dartgame.repository.SessionRepository;
import lux.dartgame.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    private static final String OWNER_USERNAME = "alice";
    private static final String PLAYER_USERNAME = "bob";
    private static final Instant PLAYED_AT = Instant.parse("2026-09-01T10:00:00Z");

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GametypeRepository gametypeRepository;

    @InjectMocks
    private SessionService sessionService;

    private User user(final String username) {
        User user = new User();
        user.setUserName(username);
        return user;
    }

    private User user(final String username, final long userId) {
        User user = user(username);
        user.setUserId(userId);
        return user;
    }

    private Game existingGame(final Session session, final String gametypeName) {
        Gametype gametype = new Gametype();
        gametype.setGametype(gametypeName);
        Game game = new Game();
        game.setGametype(gametype);
        session.addGame(game);
        return game;
    }

    private Session session(final long id, final boolean active, final User owner) {
        Session session = new Session();
        session.setSessionId(id);
        session.setActive(active);
        session.setOwner(owner);
        session.setPlayedAt(PLAYED_AT);
        return session;
    }

    private void stubSavePopulatingPlayedAt() {
        when(sessionRepository.save(any())).thenAnswer(invocation -> {
            Session session = invocation.getArgument(0);
            session.setPlayedAt(PLAYED_AT);
            return session;
        });
    }

    @Test
    void createSession_withoutGamesOrPlayers_addsOwnerAndSaves() {
        User owner = user(OWNER_USERNAME);
        when(userRepository.findByUserName(OWNER_USERNAME)).thenReturn(Optional.of(owner));
        stubSavePopulatingPlayedAt();

        SessionResponse result = sessionService.createSession(
                Optional.empty(), Optional.empty(), OWNER_USERNAME);

        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(captor.capture());
        Session saved = captor.getValue();
        assertThat(saved.getOwner()).isEqualTo(owner);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getPlayers()).containsExactly(owner);
        assertThat(result).isEqualTo(new SessionResponse(saved.getSessionId(),
                PLAYED_AT.toString(), List.of(), true));
    }

    @Test
    void createSession_withPlayers_addsOwnerAutomatically() {
        User owner = user(OWNER_USERNAME);
        User player = user(PLAYER_USERNAME);
        when(userRepository.findByUserName(OWNER_USERNAME)).thenReturn(Optional.of(owner));
        when(userRepository.findByUserName(PLAYER_USERNAME)).thenReturn(Optional.of(player));
        stubSavePopulatingPlayedAt();

        sessionService.createSession(
                Optional.empty(),
                Optional.of(Set.of(new UserRequest(PLAYER_USERNAME))),
                OWNER_USERNAME);

        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(captor.capture());
        Session saved = captor.getValue();
        assertThat(saved.getPlayers()).containsExactlyInAnyOrder(owner, player);
    }

    @Test
    void createSession_unknownOwner_throwsAndDoesNotSave() {
        when(userRepository.findByUserName("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.createSession(
                Optional.empty(), Optional.empty(), "ghost"))
                .isInstanceOf(UsernameNotFoundException.class);

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void createSession_unknownPlayer_throwsAndDoesNotSave() {
        User owner = user(OWNER_USERNAME);
        when(userRepository.findByUserName(OWNER_USERNAME)).thenReturn(Optional.of(owner));
        when(userRepository.findByUserName("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.createSession(
                Optional.empty(),
                Optional.of(Set.of(new UserRequest("ghost"))),
                OWNER_USERNAME))
                .isInstanceOf(UsernameNotFoundException.class);

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void createSession_withValidGametype_attachesGameToSession() {
        User owner = user(OWNER_USERNAME);
        Gametype gametype = new Gametype();
        gametype.setGametype("501");
        when(userRepository.findByUserName(OWNER_USERNAME)).thenReturn(Optional.of(owner));
        when(gametypeRepository.findByGametype("501")).thenReturn(Optional.of(gametype));
        stubSavePopulatingPlayedAt();

        sessionService.createSession(
                Optional.of(List.of(new GameRequest("501"))),
                Optional.empty(),
                OWNER_USERNAME);

        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(captor.capture());
        Session saved = captor.getValue();
        assertThat(saved.getGames()).hasSize(1);
        assertThat(saved.getGames().get(0).getGametype()).isEqualTo(gametype);
        assertThat(saved.getGames().get(0).getSession()).isEqualTo(saved);
        assertThat(saved.getGames().get(0).getGameStats())
                .extracting(GameStat::getPoints).containsExactly(501);
    }

    @Test
    void createSession_unknownGametype_throwsAndDoesNotSave() {
        User owner = user(OWNER_USERNAME);
        when(userRepository.findByUserName(OWNER_USERNAME)).thenReturn(Optional.of(owner));
        when(gametypeRepository.findByGametype("999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.createSession(
                Optional.of(List.of(new GameRequest("999"))),
                Optional.empty(),
                OWNER_USERNAME))
                .isInstanceOf(GameModeNotFoundException.class);

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void getSession_returnsAllSessionsForUser() {
        User owner = user(OWNER_USERNAME);
        when(userRepository.findByUserName(OWNER_USERNAME)).thenReturn(Optional.of(owner));
        when(sessionRepository.findByOwner(owner)).thenReturn(
                List.of(session(1L, true, owner), session(2L, true, owner)));

        List<SessionResponse> result = sessionService.getSession(OWNER_USERNAME,
                Optional.empty());

        assertThat(result).containsExactly(
                new SessionResponse(1L, PLAYED_AT.toString(), List.of(), true),
                new SessionResponse(2L, PLAYED_AT.toString(), List.of(), true));
    }

    @Test
    void getSession_unknownUser_throws() {
        when(userRepository.findByUserName("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.getSession("ghost", Optional.empty()))
                .isInstanceOf(UsernameNotFoundException.class);

        verify(sessionRepository, never()).findByOwner(any());
    }

    @Test
    void getSession_noSessions_throws() {
        User owner = user(OWNER_USERNAME);
        when(userRepository.findByUserName(OWNER_USERNAME)).thenReturn(Optional.of(owner));
        when(sessionRepository.findByOwner(owner)).thenReturn(List.of());

        assertThatThrownBy(() -> sessionService.getSession(OWNER_USERNAME, Optional.empty()))
                .isInstanceOf(NoSessionsForThisUserException.class);
    }

    @Test
    void getSession_withGameType_returnsOnlySessionsContainingIt() {
        User owner = user(OWNER_USERNAME);
        Session with301 = session(1L, true, owner);
        existingGame(with301, "301");
        Session with501 = session(2L, true, owner);
        existingGame(with501, "501");

        when(userRepository.findByUserName(OWNER_USERNAME)).thenReturn(Optional.of(owner));
        when(sessionRepository.findByOwner(owner)).thenReturn(List.of(with301, with501));

        List<SessionResponse> result = sessionService.getSession(OWNER_USERNAME,
                Optional.of(new GameRequest("301")));

        assertThat(result).containsExactly(
                new SessionResponse(1L, PLAYED_AT.toString(),
                        List.of(new GametypeResponse("301")), true));
    }

    @Test
    void getSession_withGameType_keepsEveryGametypeOfAMatchedSession() {
        User owner = user(OWNER_USERNAME);
        Session both = session(1L, true, owner);
        existingGame(both, "301");
        existingGame(both, "501");

        when(userRepository.findByUserName(OWNER_USERNAME)).thenReturn(Optional.of(owner));
        when(sessionRepository.findByOwner(owner)).thenReturn(List.of(both));

        List<SessionResponse> result = sessionService.getSession(OWNER_USERNAME,
                Optional.of(new GameRequest("301")));

        assertThat(result).containsExactly(
                new SessionResponse(1L, PLAYED_AT.toString(),
                        List.of(new GametypeResponse("301"),
                                new GametypeResponse("501")), true));
    }

    @Test
    void getSession_gameTypeInNoSession_throws() {
        User owner = user(OWNER_USERNAME);
        Session with501 = session(1L, true, owner);
        existingGame(with501, "501");

        when(userRepository.findByUserName(OWNER_USERNAME)).thenReturn(Optional.of(owner));
        when(sessionRepository.findByOwner(owner)).thenReturn(List.of(with501));

        assertThatThrownBy(() -> sessionService.getSession(OWNER_USERNAME,
                Optional.of(new GameRequest("301"))))
                .isInstanceOf(NoSessionsForThisUserException.class);
    }

    @Test
    void getSession_withNullGameType_doesNotFilter() {
        User owner = user(OWNER_USERNAME);
        Session with501 = session(1L, true, owner);
        existingGame(with501, "501");

        when(userRepository.findByUserName(OWNER_USERNAME)).thenReturn(Optional.of(owner));
        when(sessionRepository.findByOwner(owner)).thenReturn(List.of(with501));

        List<SessionResponse> result = sessionService.getSession(OWNER_USERNAME,
                Optional.of(new GameRequest(null)));

        assertThat(result).containsExactly(
                new SessionResponse(1L, PLAYED_AT.toString(),
                        List.of(new GametypeResponse("501")), true));
    }

    @Test
    void deleteSession_ownerDeletesOwnActiveSession() {
        User owner = user(OWNER_USERNAME);
        Session ownedSession = session(7L, true, owner);
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(ownedSession));
        when(userRepository.findRoleByUserName(OWNER_USERNAME)).thenReturn(Optional.of("USER"));

        sessionService.deleteSession(7L, OWNER_USERNAME);

        verify(sessionRepository).delete(ownedSession);
    }

    @Test
    void deleteSession_ownerCannotDeleteOwnInactiveSession() {
        User owner = user(OWNER_USERNAME);
        when(sessionRepository.findById(7L)).thenReturn(
                Optional.of(session(7L, false, owner)));
        when(userRepository.findRoleByUserName(OWNER_USERNAME)).thenReturn(Optional.of("USER"));

        assertThatThrownBy(() -> sessionService.deleteSession(7L, OWNER_USERNAME))
                .isInstanceOf(AccessDeniedException.class);

        verify(sessionRepository, never()).delete(any());
    }

    @Test
    void deleteSession_adminDeletesOthersInactiveSession() {
        User owner = user(OWNER_USERNAME);
        Session ownedSession = session(7L, false, owner);
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(ownedSession));
        when(userRepository.findRoleByUserName("admin")).thenReturn(Optional.of("ADMIN"));

        sessionService.deleteSession(7L, "admin");

        verify(sessionRepository).delete(ownedSession);
    }

    @Test
    void deleteSession_adminDeletesActiveSession() {
        User owner = user(OWNER_USERNAME);
        Session ownedSession = session(7L, true, owner);
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(ownedSession));
        when(userRepository.findRoleByUserName("admin")).thenReturn(Optional.of("ADMIN"));

        sessionService.deleteSession(7L, "admin");

        verify(sessionRepository).delete(ownedSession);
    }

    @Test
    void deleteSession_nonOwnerNonAdminCannotDelete() {
        User owner = user(PLAYER_USERNAME);
        when(sessionRepository.findById(7L)).thenReturn(
                Optional.of(session(7L, true, owner)));
        when(userRepository.findRoleByUserName(OWNER_USERNAME)).thenReturn(Optional.of("USER"));

        assertThatThrownBy(() -> sessionService.deleteSession(7L, OWNER_USERNAME))
                .isInstanceOf(AccessDeniedException.class);

        verify(sessionRepository, never()).delete(any());
    }

    @Test
    void deleteSession_unknownSession_throws() {
        when(sessionRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.deleteSession(7L, OWNER_USERNAME))
                .isInstanceOf(SessionNotFoundException.class);

        verify(sessionRepository, never()).delete(any());
    }

    @Test
    void addGames_ownerAddsGame_attachesGameAndStats() {
        User owner = user(OWNER_USERNAME, 1L);
        User player = user(PLAYER_USERNAME, 2L);
        Session session = session(7L, true, owner);
        session.addPlayers(owner);
        session.addPlayers(player);
        Gametype gametype = new Gametype();
        gametype.setGametype("501");
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(session));
        when(gametypeRepository.findByGametype("501")).thenReturn(Optional.of(gametype));

        SessionResponse result = sessionService.addGames(7L,
                List.of(new GameRequest("501")), OWNER_USERNAME);

        assertThat(session.getGames()).hasSize(1);
        Game game = session.getGames().get(0);
        assertThat(game.getGametype()).isEqualTo(gametype);
        assertThat(game.getNrOfPlayers()).isEqualTo(2);
        assertThat(game.getSession()).isEqualTo(session);
        assertThat(game.getGameStats()).hasSize(2);
        assertThat(game.getGameStats()).allMatch(stat -> stat.getTurn() == 0);
        assertThat(game.getGameStats()).allMatch(stat -> stat.getPoints() == 501);
        assertThat(result).isEqualTo(new SessionResponse(7L, PLAYED_AT.toString(),
                List.of(new GametypeResponse("501")), true));
        verify(sessionRepository).findById(7L);
    }

    @Test
    void addGames_singlePlayer_oneStatAtPositionZero() {
        User owner = user(OWNER_USERNAME, 1L);
        Session session = session(7L, true, owner);
        session.addPlayers(owner);
        Gametype gametype = new Gametype();
        gametype.setGametype("501");
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(session));
        when(gametypeRepository.findByGametype("501")).thenReturn(Optional.of(gametype));

        sessionService.addGames(7L, List.of(new GameRequest("501")), OWNER_USERNAME);

        Game game = session.getGames().get(0);
        assertThat(game.getGameStats()).hasSize(1);
        GameStat stat = game.getGameStats().get(0);
        assertThat(stat.getPosition()).isZero();
        assertThat(stat.getTurn()).isZero();
        assertThat(stat.getUser()).isEqualTo(owner);
    }

    @Test
    void addGames_rotatesUsingExistingGamesOfSameGametype() {
        User owner = user(OWNER_USERNAME, 1L);
        User player = user(PLAYER_USERNAME, 2L);
        Session session = session(7L, true, owner);
        session.addPlayers(owner);
        session.addPlayers(player);
        existingGame(session, "501");
        Gametype gametype = new Gametype();
        gametype.setGametype("501");
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(session));
        when(gametypeRepository.findByGametype("501")).thenReturn(Optional.of(gametype));

        sessionService.addGames(7L, List.of(new GameRequest("501")), OWNER_USERNAME);

        Game newGame = session.getGames().get(1);
        List<GameStat> stats = newGame.getGameStats();
        assertThat(stats).hasSize(2);
        assertThat(stats.get(0).getPosition()).isZero();
        assertThat(stats.get(0).getUser()).isEqualTo(player);
        assertThat(stats.get(1).getPosition()).isEqualTo(1);
        assertThat(stats.get(1).getUser()).isEqualTo(owner);
    }

    @Test
    void addGames_differentGametype_doesNotShareOccurrence() {
        User owner = user(OWNER_USERNAME, 1L);
        User player = user(PLAYER_USERNAME, 2L);
        Session session = session(7L, true, owner);
        session.addPlayers(owner);
        session.addPlayers(player);
        existingGame(session, "501");
        Gametype gametype = new Gametype();
        gametype.setGametype("301");
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(session));
        when(gametypeRepository.findByGametype("301")).thenReturn(Optional.of(gametype));

        sessionService.addGames(7L, List.of(new GameRequest("301")), OWNER_USERNAME);

        Game newGame = session.getGames().get(1);
        List<GameStat> stats = newGame.getGameStats();
        assertThat(stats.get(0).getPosition()).isZero();
        assertThat(stats.get(0).getUser()).isEqualTo(owner);
        assertThat(stats).extracting(GameStat::getPoints).containsExactly(301, 301);
    }

    @Test
    void addGames_multipleGamesInOneCall_incrementsOffsetSequentially() {
        User owner = user(OWNER_USERNAME, 1L);
        User player = user(PLAYER_USERNAME, 2L);
        Session session = session(7L, true, owner);
        session.addPlayers(owner);
        session.addPlayers(player);
        existingGame(session, "301");
        Gametype gametype = new Gametype();
        gametype.setGametype("301");
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(session));
        when(gametypeRepository.findByGametype("301")).thenReturn(Optional.of(gametype));

        sessionService.addGames(7L,
                List.of(new GameRequest("301"), new GameRequest("301")), OWNER_USERNAME);

        Game first = session.getGames().get(1);
        Game second = session.getGames().get(2);
        assertThat(first.getGameStats().get(0).getUser()).isEqualTo(player);
        assertThat(second.getGameStats().get(0).getUser()).isEqualTo(owner);
    }

    @Test
    void addGames_nonOwner_throwsAccessDenied() {
        User owner = user(OWNER_USERNAME, 1L);
        Session session = session(7L, true, owner);
        session.addPlayers(owner);
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.addGames(7L,
                List.of(new GameRequest("501")), PLAYER_USERNAME))
                .isInstanceOf(AccessDeniedException.class);

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void addGames_inactiveSession_throwsAccessDenied() {
        User owner = user(OWNER_USERNAME, 1L);
        Session session = session(7L, false, owner);
        session.addPlayers(owner);
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.addGames(7L,
                List.of(new GameRequest("501")), OWNER_USERNAME))
                .isInstanceOf(AccessDeniedException.class);

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void addGames_unknownSession_throwsSessionNotFoundException() {
        when(sessionRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.addGames(7L,
                List.of(new GameRequest("501")), OWNER_USERNAME))
                .isInstanceOf(SessionNotFoundException.class);

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void addGames_unknownGametype_throwsGameModeNotFoundException() {
        User owner = user(OWNER_USERNAME, 1L);
        Session session = session(7L, true, owner);
        session.addPlayers(owner);
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(session));
        when(gametypeRepository.findByGametype("999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.addGames(7L,
                List.of(new GameRequest("999")), OWNER_USERNAME))
                .isInstanceOf(GameModeNotFoundException.class);

        assertThat(session.getGames()).isEmpty();
        verify(sessionRepository, never()).save(any());
    }
}
