package lux.dartgame.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.math.NumberUtils.toLong;

import lux.dartgame.repository.GameRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;
import lux.dartgame.dto.FinishedGameResponse;
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

@Slf4j
@Service
public class SessionService {

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final GametypeRepository gametypeRepository;
    private final GameRepository gameRepository;

    @Autowired
    public SessionService(final SessionRepository sessionRepositoryParam,
                          final UserRepository userRepositoryParam,
                          final GametypeRepository gametypeRepositoryParam,
                          final GameRepository gameRepositoryParam) {
        this.sessionRepository = sessionRepositoryParam;
        this.userRepository = userRepositoryParam;
        this.gametypeRepository = gametypeRepositoryParam;
        this.gameRepository = gameRepositoryParam;
    }

    @Transactional(readOnly = true)
    public SessionResponse displaySession(final long sessionId) {

        Session session = sessionRepository.findById(sessionId)
                                           .orElseThrow(SessionNotFoundException::new);

        return new SessionResponse(sessionId,
                                   session.getPlayedAt().toString(),
                                    session.getGames()
                                            .stream()
                                            .map(Game::getGametype)
                                            .map(g -> new GametypeResponse(
                                                    g.getGametype()))
                                            .toList(),
                                    session.isActive());
    }

    @Transactional(readOnly = true)
    public List<FinishedGameResponse> getAllFinishedGames(final long sessionId) {
        Session session = sessionRepository.findById(sessionId)
                                           .orElseThrow(SessionNotFoundException::new);
       return session.getGames()
                     .stream()
                     .filter(g -> g.getWinner() != null)
                     .map(g -> new FinishedGameResponse(
                                           g.getGametype().getGametype(),
                                           g.getGameId(),
                                           g.getWinner().getUserName()))
                     .toList();
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> getSession(final String username,
                                            final Optional<GameRequest> gameRequest) {
        log.info("Looking for all sessions owned by {}", username);

        User owner = userRepository.findByUserName(username)
                                   .orElseThrow(UsernameNotFoundException::new);

        List<Session> sessions = sessionRepository.findByOwner(owner);

        // Spring may hand over Optional.empty() or a present request with a null
        // gameType, so normalise to a plain String before filtering.
        if (gameRequest.isPresent() && gameRequest.get().gameType() != null) {
            String gametype = gameRequest.get().gameType();
            log.info("Filtering sessions of {} by gametype {}", username, gametype);
            sessions = sessions.stream()
                    .filter(s -> s.getGames()
                            .stream()
                            .anyMatch(g -> gametype.equals(g.getGametype().getGametype())))
                    .toList();
        }

        if (sessions.isEmpty()) {
            throw new NoSessionsForThisUserException(owner.getUserName());
        }

        return sessions.stream()
                .map(s -> new SessionResponse(s.getSessionId(),
                                                      s.getPlayedAt().toString(),
                                                      s.getGames()
                                                          .stream()
                                                          .map(Game::getGametype)
                                                          .map(g -> new GametypeResponse(
                                                                  g.getGametype()))
                                                          .toList(),
                                                      s.isActive()))
                .collect(Collectors.toList());
    }

    @Transactional
    public SessionResponse createSession(final Optional<List<GameRequest>> games,
                                         final Optional<Set<UserRequest>> players,
                                         final String username) {
        log.info("Attempting to create session for {}", username);

        User owner = userRepository.findByUserName(username)
                                   .orElseThrow(UsernameNotFoundException::new);

        Session session = new Session();
        session.setOwner(owner);
        session.setActive(true);

        if (players.isEmpty()) {
            session.addPlayers(owner);
        } else {
            log.info("Attempting to add players to session");
            session.setPlayers(players.get().stream()
                    .map(player -> userRepository.findByUserName(player.username())
                            .orElseThrow(UsernameNotFoundException::new))
                    .peek(player -> log.info("Adding player {} to session", player))
                    .collect(Collectors.toSet()));

            // The owner is always a player in their own session.
            session.addPlayers(owner);
        }

        List<User> orderedPlayers = new ArrayList<>(session.getPlayers());
        orderedPlayers.sort(Comparator.comparing(User::getUserId));

        games.ifPresent(gameRequests -> buildGames(session,
                                                                     orderedPlayers,
                                                                     gameRequests,
                                                                     new HashMap<>()));

        sessionRepository.save(session);
        log.info("Session created successfully for {}", username);
        return new SessionResponse(session.getSessionId(),
                                    session.getPlayedAt().toString(),
                                    session.getGames().stream()
                                            .map(Game::getGametype)
                                            .map(g -> new GametypeResponse(
                                                    g.getGametype()))
                                            .toList(),
                                    session.isActive());
    }

    public void deleteSession(final long sessionId, final String username) {
        log.info("{} wants to delete session {}", username, sessionId);

        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(SessionNotFoundException::new);

        boolean isOwner = session.getOwner().getUserName().equals(username);
        boolean isAdmin = "ADMIN".equals(userRepository
                .findRoleByUserName(username)
                .orElseThrow(UsernameNotFoundException::new));

        // If session is not active and user is not the owner, or user is not admin
        boolean canDelete = (isOwner && session.isActive())
                || isAdmin;

        if (!canDelete) {
            throw new AccessDeniedException();
        }

        sessionRepository.delete(session);
        log.info("Successfully deleted session {}", sessionId);
    }

    @Transactional
    public void finishSession(final long sessionId,
                              final String username) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(SessionNotFoundException::new);

        if (!session.getOwner().getUserName().equals(username)) {
            throw new AccessDeniedException();
        }

        int before = session.getGames().size();
        session.getGames().removeIf(g -> g.getWinner() == null);
        log.info("Session {} had {} unfinished games removed",
                session.getSessionId(), before - session.getGames().size());

        if (session.getGames().isEmpty()) {
            sessionRepository.delete(session);
            log.info("Successfully deleted session {}", sessionId);
        } else {
            session.setActive(false);
            log.info("Successfully finished session {}", session.getSessionId());
        }
    }

    @Transactional
    public SessionResponse addGames(final long sessionId,
                                    final List<GameRequest> games,
                                    final String username) {
        log.info("{} wants to add games to session {}", username, sessionId);

        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(SessionNotFoundException::new);

        if (!session.getOwner().getUserName().equals(username)) {
            throw new AccessDeniedException();
        }

        if (!session.isActive()) {
            throw new AccessDeniedException();
        }

        List<User> orderedPlayers = new ArrayList<>(session.getPlayers());
        orderedPlayers.sort(Comparator.comparing(User::getUserId));

        Map<String, Integer> gametypeCounts = session.getGames().stream()
                .collect(Collectors.toMap(
                        g -> g.getGametype().getGametype(),
                        g -> 1,
                        Integer::sum));

        buildGames(session, orderedPlayers, games, gametypeCounts);
        log.info("Games added to session {} by {}", sessionId, username);

        return new SessionResponse(session.getSessionId(),
                session.getPlayedAt().toString(),
                session.getGames().stream()
                        .map(Game::getGametype)
                        .map(g -> new GametypeResponse(
                                g.getGametype()))
                        .toList(),
                session.isActive());
    }

    private void buildGames(final Session session,
                                final List<User> orderedPlayers,
                                final List<GameRequest> gamemodes,
                                final Map<String, Integer> gametypeCounts) {
        int n = orderedPlayers.size();

        for (GameRequest gamemode : gamemodes) {
            Gametype gametype = gametypeRepository.findByGametype(gamemode.gameType())
                    .orElseThrow(() -> new GameModeNotFoundException(gamemode.gameType()));

            int occurrence = gametypeCounts.getOrDefault(gametype.getGametype(), 0);
            gametypeCounts.put(gametype.getGametype(), occurrence + 1);
            int offset = n == 0 ? 0 : occurrence % n;

            Game game = new Game();
            game.setGametype(gametype);
            game.setNrOfPlayers(n);
            session.addGame(game);

            for (int i = 0; i < n; i++) {
                GameStat stat = new GameStat();
                stat.setPoints(Integer.parseInt(gametype.getGametype()));
                stat.setUser(orderedPlayers.get((i + offset) % n));
                stat.setGame(game);
                stat.setPosition(i);
                stat.setTurn(0);
                game.getGameStats().add(stat);
            }
        }
    }
}
