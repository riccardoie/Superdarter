package lux.dartgame.service;

import lombok.extern.slf4j.Slf4j;
import lux.dartgame.constants.Constants;
import lux.dartgame.dto.BoardHitResponse;
import lux.dartgame.dto.PlayerStatResponse;
import lux.dartgame.dto.UserRequest;
import lux.dartgame.repository.PlayerStatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class PlayerStatsService {
    private final PlayerStatRepository playerStatRepository;

    public PlayerStatsService(final PlayerStatRepository playerStatRepositoryParam) {
        this.playerStatRepository = playerStatRepositoryParam;
    }

    @Transactional(readOnly = true)
    public List<PlayerStatResponse> getPlayerStats(final List<UserRequest> players) {
        if (players == null || players.isEmpty() || players.size() > 2) {
            throw new IllegalArgumentException("Expected 1 or 2 players");
        }
        List<String> usernames = players.stream().map(UserRequest::username).toList();
        return playerStatRepository.findByPlayerUserNameIn(usernames).stream()
                .map(stat -> new PlayerStatResponse(stat.getPlayer().getUserName(),
                        stat.getAvgPoints(),
                        stat.getWonMatches(),
                        stat.getPlayedMatches(),
                        stat.getTriple20s(),
                        stat.getBullseyes(),
                        stat.getHighestScore(),
                        stat.getHighestCheckout(),
                        stat.getWonLegs(),
                        stat.getPlayedLegs(),
                        playerStatRepository.findCompletedDartsThrown(
                                stat.getPlayer().getUserName()),
                        boardHits(stat.getPlayer().getUserName())))
                .toList();
    }

    private Map<Integer, BoardHitResponse> boardHits(final String username) {
        Map<Integer, BoardHitResponse> hits = new LinkedHashMap<>();
        BoardHitResponse empty = new BoardHitResponse(0, 0, 0, 0);
        for (int number = 1; number <= Constants.MAX_DART_NUMBER; number++) {
            hits.put(number, empty);
        }
        hits.put(Constants.BULLSEYE_VALUE, empty);
        for (var total : playerStatRepository.findBoardHits(username)) {
            long singles = total.getSingleHits();
            long doubles = total.getDoubleHits();
            long triples = total.getTripleHits();
            hits.put(total.getBoardNumber(),
                    new BoardHitResponse(singles + doubles + triples, singles, doubles, triples));
        }
        return hits;
    }
}
