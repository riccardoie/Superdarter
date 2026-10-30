package lux.dartgame.service;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/** Draw once per new match, never per leg or reload. */
@Component
public class StartingPlayerSelector {
    public int choose(final int playerCount) {
        return ThreadLocalRandom.current().nextInt(playerCount);
    }
}
