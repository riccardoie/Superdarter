package lux.dartgame.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StartingPlayerSelectorTest {
    @Test
    void drawsOnlyValidParticipantPositionsIncludingSoloGames() {
        var selector = new StartingPlayerSelector();
        assertThat(selector.choose(1)).isZero();
        for (int players = 2; players <= 10; players++) {
            for (int draw = 0; draw < 100; draw++) {
                assertThat(selector.choose(players)).isBetween(0, players - 1);
            }
        }
    }
}
