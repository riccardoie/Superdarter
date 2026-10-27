package lux.dartgame.service;

import lombok.extern.slf4j.Slf4j;
import lux.dartgame.dto.PlayerStatResponse;
import lux.dartgame.dto.UserRequest;
import lux.dartgame.repository.PlayerStatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
                                stat.getPlayer().getUserName())))
                .toList();
    }
}
