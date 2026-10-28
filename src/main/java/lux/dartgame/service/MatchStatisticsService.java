package lux.dartgame.service;

import java.util.Comparator;
import java.util.List;
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

    // Rebuild from retained completed legs so undo can also lower personal-best records safely.
    public void rebuildTotals(final Leg reopened) {
        for (LegPlayerStat participant : reopened.getPlayerStats().stream()
                .sorted(Comparator.comparingLong(s -> s.getUser().getUserId())).toList()) {
            User user = users.findForUpdate(participant.getUser().getUserId()).orElseThrow();
            PlayerStat total = user.getStats();
            entities.refresh(total);
            List<LegPlayerStat> completed = entities.createQuery(
                    "select s from LegPlayerStat s join fetch s.leg l join fetch l.match m "
                            + "join fetch m.gametype where s.user.userId = :user "
                            + "and l.status = lux.dartgame.model.MatchStatus.COMPLETED",
                    LegPlayerStat.class).setParameter("user", user.getUserId()).getResultList();
            total.setPlayedLegs(completed.size());
            total.setWonLegs((int) completed.stream()
                    .filter(s -> s.getLeg().getWinner().getUserId() == user.getUserId()).count());
            var matches = completed.stream().map(s -> s.getLeg().getMatch())
                    .filter(m -> m.getStatus() == lux.dartgame.model.MatchStatus.COMPLETED)
                    .distinct().toList();
            total.setPlayedMatches(matches.size());
            total.setWonMatches((int) matches.stream()
                    .filter(m -> m.getWinner().getUserId() == user.getUserId()).count());
            total.setAvgPoints((float) completed.stream().mapToDouble(s -> s.getTurn() == 0 ? 0
                    : (double) (CountdownRules.startingPoints(
                            s.getLeg().getMatch().getGametype().getGametype())
                    - s.getPoints()) / s.getTurn()).average().orElse(0));
            total.setTriple20s(completed.stream().mapToInt(LegPlayerStat::getTriple20s).sum());
            total.setBullseyes(completed.stream().mapToInt(LegPlayerStat::getBullseyes).sum());
            total.setHighestScore(completed.stream().mapToInt(LegPlayerStat::getHighestScore)
                    .max().orElse(0));
            total.setHighestCheckout(completed.stream().mapToInt(LegPlayerStat::getHighestCheckout)
                    .max().orElse(0));
        }
    }
}
