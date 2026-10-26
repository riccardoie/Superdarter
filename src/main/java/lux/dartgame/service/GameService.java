package lux.dartgame.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;
import lux.dartgame.constants.Constants;
import lux.dartgame.dto.GameResponse;
import lux.dartgame.dto.GameStatResponse;
import lux.dartgame.dto.PlayedRoundRequest;
import lux.dartgame.exception.AccessDeniedException;
import lux.dartgame.exception.GameModeNotFoundException;
import lux.dartgame.exception.GameNotFoundException;
import lux.dartgame.exception.GameStatNotFoundException;
import lux.dartgame.exception.InvalidScoreException;
import lux.dartgame.exception.InvalidTurnException;
import lux.dartgame.exception.NoAvailableGameException;
import lux.dartgame.exception.SessionNotFoundException;
import lux.dartgame.exception.UsernameNotFoundException;
import lux.dartgame.model.Game;
import lux.dartgame.model.GameStat;
import lux.dartgame.model.GameStatsId;
import lux.dartgame.model.PlayerStat;
import lux.dartgame.model.Session;
import lux.dartgame.model.User;
import lux.dartgame.repository.GameRepository;
import lux.dartgame.repository.GameStatRepository;
import lux.dartgame.repository.SessionRepository;
import lux.dartgame.repository.UserRepository;


@Slf4j
@Service
public class GameService {
    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final GameRepository gameRepository;
    private final GameStatRepository gameStatRepository;

    @Autowired
    public GameService(final SessionRepository sessionRepositoryParam,
                       final UserRepository userRepositoryParam,
                       final GameRepository gameRepositoryParam,
                       final GameStatRepository gameStatRepositoryParam) {
        this.sessionRepository = sessionRepositoryParam;
        this.userRepository = userRepositoryParam;
        this.gameRepository = gameRepositoryParam;
        this.gameStatRepository = gameStatRepositoryParam;
    }

    // Given a list of games, it filters out games with a winner.
    // Prefer games where a player has already taken a turn.
    // It returns the game id of the first game that matches the criteria.
    // If no game matches the criteria, it returns the first game with no winner.
    // If no game matches any of the criteria above, it returns null.
    private Game getAvailableGame(final List<Game> games) {
        return games.stream()
                .filter(game -> game.getWinner() == null
                        && game.getGameStats().stream().anyMatch(stat -> stat.getTurn() > 0))
                .findFirst()
                .orElseGet(() -> games.stream()
                        .filter(game -> game.getWinner() == null)
                        .findFirst()
                        .orElseThrow(NoAvailableGameException::new));
    }

    // Find a game where no player has taken a turn yet.
    private Game getUnstartedGames(final List<Game> games) {
        return games.stream()
                .filter(game -> game.getWinner() == null
                        && game.getGameStats().stream()
                                .noneMatch(stat ->
                                                stat.getTurn() > 0))
                .findFirst()
                .orElseThrow(NoAvailableGameException::new);
    }

    // Find the next player in turn for a game.
    // Iterates through the game stats and find the one with the lowest turn and position.
    private long getNextInTurn(final Game game) {
        return game.getGameStats().stream()
                .min(Comparator.comparingInt(GameStat::getTurn)
                        .thenComparingInt(GameStat::getPosition))
                .map(stat -> stat.getUser().getUserId())
                .orElseThrow(NoAvailableGameException::new);
    }

    @Transactional
    public GameResponse startGame(final String gametype,
                                  final long sessionId,
                                  final String player) {
        log.info("Looking for {} in Session {}", gametype, sessionId);

        Session session = sessionRepository.findById(sessionId)
                                           .orElseThrow(SessionNotFoundException::new);

        boolean isMember = session.getOwner().getUserName().equals(player)
                || session.getPlayers().stream().anyMatch(p -> p.getUserName().equals(player));
        if (!isMember) {
            throw new AccessDeniedException();
        }

        List<Game> games = session.getGames();
        games = games.stream()
                    .filter(game -> game.getGametype()
                    .getGametype().equals(gametype)).toList();

        log.info("Found {} games", games.size());

        Game game = getAvailableGame(games);
        long userId = getNextInTurn(game);
        String username = userRepository.findUserNameById(userId)
                .orElseThrow(UsernameNotFoundException::new);

        return new GameResponse(
                gametype,
                username,
                game.getGameId(),
                game.getWinner() != null
        );
    }

    // Ordered number/multiplier pairs; 0 1 represents a thrown miss.
    private int[] getScoreFromRound(final String score) {
        if (score == null) {
            throw new InvalidScoreException();
        }
        if (score.isBlank()) {
            return new int[0]; // Legacy clients submit an empty string for an all-miss turn.
        }
        String[] tokens = score.trim().split("\\s+");
        if (tokens.length % 2 != 0 || tokens.length / 2 > Constants.MAX_DARTS_PER_TURN) {
            throw new InvalidScoreException();
        }
        int[] results = new int[tokens.length];
        try {
            for (int i = 0; i < tokens.length; i++) {
                results[i] = Integer.parseInt(tokens[i]);
            }
        } catch (NumberFormatException e) {
            throw new InvalidScoreException();
        }
        for (int i = 0; i < results.length; i += 2) {
            int number = results[i];
            int multiplier = results[i + 1];
            boolean isBullseye = number == Constants.BULLSEYE_VALUE;
            boolean isNumber = number >= 1 && number <= Constants.MAX_DART_NUMBER;
            boolean validMultiplier = (number == 0 && multiplier == 1) || (isBullseye
                    ? (multiplier == 1 || multiplier == 2)   // bullseye is 25x1 or 25x2
                    : isNumber && (multiplier >= 1 && multiplier <= Constants.MAX_MULTIPLIER));

            if (!validMultiplier) {
                throw new InvalidScoreException();
            }
        }
        return results;
    }

    @Transactional
    public GameResponse playRound(final PlayedRoundRequest roundResults,
                                  final String player,
                                  final long id) {

        User user = userRepository.findByUserName(roundResults.username())
                .orElseThrow(UsernameNotFoundException::new);

        Game game = gameRepository.findById(roundResults.gameId())
                .orElseThrow(NoAvailableGameException::new);

        Session session = sessionRepository.findById(id)
                .orElseThrow(SessionNotFoundException::new);

        boolean isMember = session.getOwner().getUserName().equals(player)
                || session.getPlayers().stream().anyMatch(p -> p.getUserName().equals(player));
        if (!isMember) {
            throw new AccessDeniedException();
        }

        if (game.getWinner() != null) {
            throw new InvalidTurnException();
        }

        if (user.getUserId() != getNextInTurn(game)) {
            throw new InvalidTurnException();
        }

        GameStat stat = gameStatRepository.findById(new GameStatsId(user.getUserId(),
                                                                            game.getGameId()))
                                            .orElseThrow(GameStatNotFoundException::new);
        String gametype = game.getGametype().getGametype();

        log.info("{} is playing round", user.getUserName());

        if (gameLogic(gametype, roundResults.score(), stat)) {
            game.setWinner(user);
            updatePlayerStats(game);
            return new GameResponse(gametype,
                                    game.getWinner().getUserName(),
                                    game.getGameId(),
                            true);
        }

        long nextUserId = getNextInTurn(game);
        user = userRepository.getReferenceById(nextUserId);

        return new GameResponse(gametype,
                                user.getUserName(),
                                game.getGameId(),
                                false);
    }

    private boolean gameLogic(final String gametype,
                              final String score,
                              final GameStat stat) {
        return switch (gametype) {
            case Constants.GAMETYPE_301, Constants.GAMETYPE_501 ->
                    playGame(stat, getScoreFromRound(score));
            default -> throw new GameModeNotFoundException(gametype);
        };
    }

    private void updateGameStats(final GameStat stat,
                                 final int result,
                                 final int tripleTwenties,
                                 final int bullsEyes) {
        stat.setTurn(stat.getTurn() + 1);

        stat.setTriple20s(stat.getTriple20s() + tripleTwenties);
        stat.setBullseyes(stat.getBullseyes() + bullsEyes);
        if (stat.getHighestScore() < result) {
            stat.setHighestScore(result);
        }
    }

    private boolean playGame(final GameStat stat, final int[] darts) {
        int startingPoints = stat.getPoints();
        int remaining = startingPoints;
        int triples = 0;
        int bullseyes = 0;
        boolean won = false;
        boolean bust = false;
        for (int i = 0; i < darts.length; i += 2) {
            int number = darts[i];
            int multiplier = darts[i + 1];
            remaining -= number * multiplier;
            if (number == Constants.MAX_DART_NUMBER && multiplier == Constants.MAX_MULTIPLIER) {
                triples++;
            }
            if (number == Constants.BULLSEYE_VALUE) {
                bullseyes++;
            }
            if (remaining < 0 || remaining == 1 || (remaining == 0 && multiplier != 2)) {
                bust = true;
                break;
            }
            if (remaining == 0) {
                won = true;
                break;
            }
        }
        int scored = bust ? 0 : startingPoints - remaining;
        updateGameStats(stat, scored, triples, bullseyes);
        if (!bust) {
            stat.setPoints(remaining);
        }
        if (won) {
            stat.setHighestCheckout(Math.max(stat.getHighestCheckout(), scored));
        }
        return won;
    }

    @Transactional(readOnly = true)
    public List<GameStatResponse> getGameStats(final long gameId,
                                     final Optional<List<String>> players) {

        Game game = gameRepository.findById(gameId).orElseThrow(() ->
                                            new GameNotFoundException(gameId));
        List<GameStat> gameStats = game.getGameStats();
        int startingPoints = Integer.parseInt(game.getGametype().getGametype());

        return players.<List<GameStatResponse>>map(strings -> gameStats.stream()
                .filter(stat -> strings.contains(stat.getUser().getUserName()))
                .sorted(Comparator.comparingInt(GameStat::getPosition))
                .map(stat -> new GameStatResponse(stat.getUser().getUserName(),
                                                 stat.getPoints(),
                                                 stat.getTurn(),
                                                 stat.getBullseyes(),
                                                 stat.getTriple20s(),
                                                 stat.getHighestScore(),
                                                 stat.getHighestCheckout(),
                                                 stat.getCheckoutAccuracy(),
                                                 startingPoints))
                .collect(Collectors.toList()))
                .orElseGet(() -> gameStats.stream()
                        .sorted(Comparator.comparingInt(GameStat::getPosition))
                        .map(stat -> new GameStatResponse(stat.getUser().getUserName(),
                                                           stat.getPoints(),
                                                           stat.getTurn(),
                                                           stat.getBullseyes(),
                                                           stat.getTriple20s(),
                                                           stat.getHighestScore(),
                                                           stat.getHighestCheckout(),
                                                            stat.getCheckoutAccuracy(),
                                                            startingPoints))
                        .collect(Collectors.toList()));

    }

    @Transactional
    public void deleteGame(final long sessionId,
                           final String player,
                           final String gametype) {
        log.info("Looking for {} in Session {}", gametype, sessionId);

        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(SessionNotFoundException::new);

        if (!session.getOwner().getUserName().equals(player)) {
            throw new AccessDeniedException();
        }

        List<Game> games = session.getGames();
        games = games.stream()
                .filter(game -> game.getGametype()
                        .getGametype().equals(gametype)).toList();

        Game game = getUnstartedGames(games);

        log.info("Deleting game {} in Session {}", gametype, sessionId);
        session.getGames().remove(game);   // keep the aggregate in sync
        gameRepository.delete(game);
    }

    // Called when a game finishes. Aggregates every participant's performance
    // in this game into their cumulative PlayerStat (created on first game).
    private void updatePlayerStats(final Game game) {
        User winner = game.getWinner();

        int startingPoints = switch (game.getGametype().getGametype()) {
            case Constants.GAMETYPE_301 -> Constants.TARGET_301;
            case Constants.GAMETYPE_501 -> Constants.TARGET_501;
            default -> throw new GameModeNotFoundException(
                    game.getGametype().getGametype());
        };

        for (GameStat stat : game.getGameStats()) {
            User player = stat.getUser();
            PlayerStat playerStats = player.getStats();
            if (playerStats == null) {
                playerStats = new PlayerStat();
                playerStats.setPlayer(player);
                player.setStats(playerStats);   // cascade ALL on User.stats persists it
            }

            int played = playerStats.getPlayedGames() + 1;
            playerStats.setPlayedGames(played);

            if (winner != null && winner.getUserId() == player.getUserId()) {
                playerStats.setWonGames(playerStats.getWonGames() + 1);
            }

            playerStats.setTriple20s(playerStats.getTriple20s() + stat.getTriple20s());
            playerStats.setBullseyes(playerStats.getBullseyes() + stat.getBullseyes());
            playerStats.setHighestScore(Math.max(playerStats.getHighestScore(),
                    stat.getHighestScore()));
            playerStats.setHighestCheckout(Math.max(playerStats.getHighestCheckout(),
                    stat.getHighestCheckout()));

            int scoredPoints = startingPoints - stat.getPoints();
            float gameAverage = stat.getTurn() > 0
                    ? (float) scoredPoints / stat.getTurn()
                    : 0f;

            // Running average over games played
            playerStats.setAvgPoints(
                    (playerStats.getAvgPoints() * (played - 1) + gameAverage) / played);
            playerStats.setAvgCheckoutAccuracy((playerStats.getAvgCheckoutAccuracy() * (played - 1)
                    + stat.getCheckoutAccuracy()) / played);
        }
    }
}
