package lux.dartgame.service;

import java.util.Comparator;
import java.util.List;
import jakarta.persistence.EntityManager;
import lux.dartgame.model.Leg;
import lux.dartgame.model.LegPlayerStat;
import lux.dartgame.model.PlayerStat;
import lux.dartgame.model.User;
import lux.dartgame.constants.Constants;
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
                .filter(s -> s.getUser() != null)
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
            total.setPlayedLegs(total.getPlayedLegs() + 1);
            boolean cricket = cricket(stat);
            if (!cricket) {
                long countdownLegs = entities.createQuery("select count(s) from LegPlayerStat s "
                        + "where s.user.userId = :user and s.leg.status = "
                        + "lux.dartgame.model.MatchStatus.COMPLETED "
                        + "and s.leg.match.gametype.gametype <> :cricket", Long.class)
                        .setParameter("user", user.getUserId())
                        .setParameter("cricket", Constants.GAMETYPE_CRICKET).getSingleResult();
                float average = stat.getTurn() == 0 ? 0f
                        : (float) (startingPoints - stat.getPoints()) / stat.getTurn();
                total.setAvgPoints((total.getAvgPoints() * (countdownLegs - 1) + average)
                        / countdownLegs);
            }
            if (matchCompleted) {
                total.setPlayedMatches(total.getPlayedMatches() + 1);
            }
            if (won(leg.getWinner(), user)) {
                total.setWonLegs(total.getWonLegs() + 1);
                if (matchCompleted) {
                    total.setWonMatches(total.getWonMatches() + 1);
                }
            }
            total.setTriple20s(total.getTriple20s() + stat.getTriple20s());
            total.setBullseyes(total.getBullseyes() + stat.getBullseyes());
            if (!cricket) {
                total.setHighestScore(Math.max(total.getHighestScore(), stat.getHighestScore()));
                total.setHighestCheckout(
                        Math.max(total.getHighestCheckout(), stat.getHighestCheckout()));
            }
        }
    }

    // Rebuild from retained completed legs so undo can also lower personal-best records safely.
    public void rebuildTotals(final Leg reopened) {
        for (LegPlayerStat participant : reopened.getPlayerStats().stream()
                .filter(s -> s.getUser() != null)
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
                    .filter(s -> won(s.getLeg().getWinner(), user)).count());
            var matches = completed.stream().map(s -> s.getLeg().getMatch())
                    .filter(m -> m.getStatus() == lux.dartgame.model.MatchStatus.COMPLETED)
                    .distinct().toList();
            total.setPlayedMatches(matches.size());
            total.setWonMatches((int) matches.stream()
                    .filter(m -> won(m.getWinner(), user)).count());
            total.setAvgPoints((float) completed.stream().filter(s -> !cricket(s))
                    .mapToDouble(s -> s.getTurn() == 0 ? 0
                    : (double) (CountdownRules.startingPoints(
                            s.getLeg().getMatch().getGametype().getGametype())
                    - s.getPoints()) / s.getTurn()).average().orElse(0));
            total.setTriple20s(completed.stream().mapToInt(LegPlayerStat::getTriple20s).sum());
            total.setBullseyes(completed.stream().mapToInt(LegPlayerStat::getBullseyes).sum());
            total.setHighestScore(completed.stream().filter(s -> !cricket(s))
                    .mapToInt(LegPlayerStat::getHighestScore)
                    .max().orElse(0));
            total.setHighestCheckout(completed.stream().filter(s -> !cricket(s))
                    .mapToInt(LegPlayerStat::getHighestCheckout)
                    .max().orElse(0));
        }
    }

    private boolean won(final User winner, final User user) {
        return winner != null && winner.getUserId() == user.getUserId();
    }

    private boolean cricket(final LegPlayerStat stat) {
        return Constants.GAMETYPE_CRICKET.equals(
                stat.getLeg().getMatch().getGametype().getGametype());
    }
}
