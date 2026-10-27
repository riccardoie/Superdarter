package lux.dartgame.service;

import lux.dartgame.dto.PlayerStatResponse;
import lux.dartgame.dto.UserRequest;
import lux.dartgame.model.PlayerStat;
import lux.dartgame.model.User;
import lux.dartgame.repository.PlayerStatRepository;
import lux.dartgame.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerStatsServiceTest {

    @Mock
    private PlayerStatRepository playerStatRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PlayerStatsService playerStatsService;

    private User user(final String username) {
        User user = new User();
        user.setUserName(username);
        return user;
    }

    private PlayerStat playerStat(final String username,
                                  final float avgPoints,
                                  final int wonGames,
                                  final int playedGames,
                                  final int triple20s,
                                  final int bullseyes,
                                  final int highestScore,
                                  final int highestCheckout) {
        PlayerStat stat = new PlayerStat();
        stat.setPlayer(user(username));
        stat.setAvgPoints(avgPoints);
        stat.setWonMatches(wonGames);
        stat.setPlayedMatches(playedGames);
        stat.setTriple20s(triple20s);
        stat.setBullseyes(bullseyes);
        stat.setHighestScore(highestScore);
        stat.setHighestCheckout(highestCheckout);
        return stat;
    }

    private PlayerStatResponse response(final String username,
                                        final float avgPoints,
                                        final int wonGames,
                                        final int playedGames,
                                        final int triple20s,
                                        final int bullseyes,
                                        final int highestScore,
                                        final int highestCheckout) {
        return new PlayerStatResponse(username,
                avgPoints,
                wonGames,
                playedGames,
                triple20s,
                bullseyes,
                highestScore,
                highestCheckout, 0, 0);
    }

    @Test
    void getPlayerStats_singlePlayer_returnsStatsForThatPlayer() {
        PlayerStat stat = playerStat("alice", 45.5f, 3, 5, 2, 1, 180, 120);
        when(playerStatRepository.findByPlayerUserNameIn(
                List.of("alice"))).thenReturn(List.of(stat));

        List<PlayerStatResponse> result = playerStatsService.getPlayerStats(
                List.of(new UserRequest("alice")));

        assertThat(result).containsExactly(response("alice", 45.5f, 3, 5, 2, 1, 180, 120));
    }

    @Test
    void getPlayerStats_twoPlayers_returnsStatsForBothPlayers() {
        PlayerStat alice = playerStat("alice", 40.0f, 2, 4, 1, 0, 140, 90);
        PlayerStat bob = playerStat("bob", 50.0f, 5, 6, 3, 2, 180, 121);
        when(playerStatRepository.findByPlayerUserNameIn(
                List.of("alice", "bob"))).thenReturn(List.of(alice, bob));

        List<PlayerStatResponse> result = playerStatsService.getPlayerStats(
                List.of(new UserRequest("alice"), new UserRequest("bob")));

        assertThat(result).containsExactly(
                response("alice", 40.0f, 2, 4, 1, 0, 140, 90),
                response("bob", 50.0f, 5, 6, 3, 2, 180, 121));
    }

    @Test
    void getPlayerStats_emptyList_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> playerStatsService.getPlayerStats(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Expected 1 or 2 players");
    }

    @Test
    void getPlayerStats_moreThanTwoPlayers_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> playerStatsService.getPlayerStats(
                List.of(new UserRequest("alice"),
                        new UserRequest("bob"),
                        new UserRequest("carol"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Expected 1 or 2 players");
    }
}
