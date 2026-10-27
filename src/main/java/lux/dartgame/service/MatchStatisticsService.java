package lux.dartgame.service;

import java.util.Comparator;
import jakarta.persistence.EntityManager;
import lux.dartgame.model.Leg;
import lux.dartgame.model.LegPlayerStat;
import lux.dartgame.model.PlayerStat;
import lux.dartgame.model.User;
import lux.dartgame.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class MatchStatisticsService {
    private final UserRepository users;
    private final EntityManager entities;

    public MatchStatisticsService(final UserRepository usersParam,
                                  final EntityManager entitiesParam) {
        users = usersParam;
        entities = entitiesParam;
    }

    // Each completed leg counts once; only the deciding leg awards match participation/wins.
    public void completeLeg(final Leg leg, final int startingPoints, final boolean matchCompleted) {
        for (LegPlayerStat stat : leg.getPlayerStats().stream()
                .sorted(Comparator.comparingLong(s -> s.getUser().getUserId())).toList()) {
            User user = users.findForUpdate(stat.getUser().getUserId()).orElseThrow();
            PlayerStat total = user.getStats();
            if (total == null) {
                total = new PlayerStat();
                total.setPlayer(user);
                user.setStats(total);
                entities.persist(total);
            } else {
                // Another match may have completed since this persistence context loaded User.
                entities.refresh(total);
            }
            int played = total.getPlayedLegs() + 1;
            float average = stat.getTurn() == 0 ? 0f
                    : (float) (startingPoints - stat.getPoints()) / stat.getTurn();
            total.setAvgPoints((total.getAvgPoints() * (played - 1) + average) / played);
            total.setPlayedLegs(played);
            if (matchCompleted) {
                total.setPlayedMatches(total.getPlayedMatches() + 1);
            }
            if (leg.getWinner().getUserId() == user.getUserId()) {
                total.setWonLegs(total.getWonLegs() + 1);
                if (matchCompleted) {
                    total.setWonMatches(total.getWonMatches() + 1);
                }
            }
            total.setTriple20s(total.getTriple20s() + stat.getTriple20s());
            total.setBullseyes(total.getBullseyes() + stat.getBullseyes());
            total.setHighestScore(Math.max(total.getHighestScore(), stat.getHighestScore()));
            total.setHighestCheckout(
                    Math.max(total.getHighestCheckout(), stat.getHighestCheckout()));
        }
    }
}
