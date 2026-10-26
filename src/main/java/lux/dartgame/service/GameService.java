package lux.dartgame.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
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

    // Parses the score string into an array of integers.
    // Checks if the score string is valid (numbers between 1 and 20 or 25,
    // multipliers between 1 and 3 - 1 and 2 for bullseye)
    private int[] getScoreFromRound(final String score) {
        List<Integer> results;
        try {
             results = Pattern.compile("\\d+")
                    .matcher(score)
                    .results()
                    .map(MatchResult::group)
                    .map(Integer::parseInt)
                    .toList();
        } catch (NumberFormatException e) {
            throw new InvalidScoreException();
        }

        if (results.isEmpty()) {
            return new int[]{0, 0, 0};
        }

        if (results.size() % 2 != 0) {
            // e.g. "20 3 5" (odd count of numbers)
            throw new InvalidScoreException();
        }

        int totalScore = 0;
        int tripleTwenties = 0;
        int bullseyes = 0;

        // Validate the score string
        for (int i = 0; i < results.size() / 2; i++) {
            int number = results.get(2 * i);
            int multiplier = results.get(2 * i + 1);

            boolean isBullseye = number == Constants.BULLSEYE_VALUE;
            boolean isNumber = number >= 1 && number <= Constants.MAX_DART_NUMBER;
            boolean validMultiplier = isBullseye
                    ? (multiplier == 1 || multiplier == 2)   // bullseye is 25x1 or 25x2
                    : isNumber && (multiplier >= 1 && multiplier <= Constants.MAX_MULTIPLIER);

            if (!validMultiplier) {
                throw new InvalidScoreException();
            }

            totalScore += number * multiplier;
            if (number == Constants.MAX_DART_NUMBER
                    && multiplier == Constants.MAX_MULTIPLIER) {
                tripleTwenties++;
            }
            if (isBullseye) {
                bullseyes++;
            }
        }

        return new int[]{totalScore, tripleTwenties, bullseyes};
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
        int[] round = getScoreFromRound(score);
        int result = round[0];

        if (!allowedScore(result)) {
            throw new InvalidScoreException();
        }

        updateGameStats(stat, result, round[1], round[2]);

        return switch (gametype) {
            case Constants.GAMETYPE_301 -> playGame(Constants.TARGET_301, stat, result);
            case Constants.GAMETYPE_501 -> playGame(Constants.TARGET_501, stat, result);
            default -> throw new GameModeNotFoundException(gametype);
        };
    }

    // Validates if the score is allowed
    private boolean allowedScore(final int score) {
        if (score < 0 || score > Constants.MAX_ROUND_SCORE) {
            throw new InvalidScoreException();
        }

        return !Constants.isImpossibleScore(score);
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

    private boolean playGame(final int game,
                             final GameStat stat,
                             final int result) {
        log.info("Playing game: {}, current points: {}, result: {}", game,
                                                                     stat.getPoints(),
                                                                     result);
        if (stat.getPoints() - result > 0) {
            stat.setPoints(stat.getPoints() - result);
            return false;
        } else if (stat.getPoints() - result == 0) { // exactly game = win
            stat.setPoints(0);
            return true;
        }
        // Player bust
        return false;
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
