package lux.dartgame.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import lux.dartgame.constants.Constants;
import lux.dartgame.dto.CreateGameRequest;
import lux.dartgame.dto.MatchResponse;
import lux.dartgame.dto.PlayedRoundRequest;
import lux.dartgame.dto.UserRequest;
import lux.dartgame.exception.AccessDeniedException;
import lux.dartgame.exception.GameConflictException;
import lux.dartgame.exception.GameModeNotFoundException;
import lux.dartgame.exception.GameNotFoundException;
import lux.dartgame.exception.InvalidTurnException;
import lux.dartgame.exception.UsernameNotFoundException;
import lux.dartgame.model.Leg;
import lux.dartgame.model.LegPlayerStat;
import lux.dartgame.model.Match;
import lux.dartgame.model.MatchParticipant;
import lux.dartgame.model.MatchStatus;
import lux.dartgame.model.User;
import lux.dartgame.repository.GametypeRepository;
import lux.dartgame.repository.MatchRepository;
import lux.dartgame.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchService {
    private final MatchRepository matches;
    private final UserRepository users;
    private final GametypeRepository gametypes;
    private final MatchStatisticsService statistics;

    public MatchService(final MatchRepository matchesParam, final UserRepository usersParam,
                        final GametypeRepository gametypesParam,
                        final MatchStatisticsService statisticsParam) {
        matches = matchesParam;
        users = usersParam;
        gametypes = gametypesParam;
        statistics = statisticsParam;
    }

    @Transactional
    public MatchResponse create(final CreateGameRequest request, final String actor) {
        int startingPoints = startingPoints(request.gametype());
        Match match = new Match();
        match.setOwner(users.findByUserName(actor).orElseThrow(UsernameNotFoundException::new));
        match.setGametype(gametypes.findByGametype(request.gametype())
                .orElseThrow(() -> new GameModeNotFoundException(request.gametype())));
        Set<String> names = new LinkedHashSet<>();
        names.add(actor);
        if (request.players() != null) {
            request.players().stream().map(UserRequest::username).forEach(names::add);
        }
        if (names.size() > Constants.MAX_NR_OF_PLAYERS) {
            throw new IllegalArgumentException("Maximum 10 players including the owner");
        }
        Leg leg = new Leg();
        leg.setMatch(match);
        leg.setLegNumber(1);
        match.getLegs().add(leg);
        for (String name : names) {
            User user = users.findByUserName(name).orElseThrow(UsernameNotFoundException::new);
            MatchParticipant participant = new MatchParticipant();
            participant.setMatch(match);
            participant.setUser(user);
            participant.setPosition(match.getParticipants().size());
            match.getParticipants().add(participant);
            LegPlayerStat stat = new LegPlayerStat();
            stat.setLeg(leg);
            stat.setUser(user);
            stat.setPosition(participant.getPosition());
            stat.setPoints(startingPoints);
            leg.getPlayerStats().add(stat);
        }
        matches.saveAndFlush(match);
        return response(match, actor);
    }

    @Transactional(readOnly = true)
    public List<MatchResponse> list(final String actor, final String gametype) {
        return matches.findForParticipant(actor).stream()
                .filter(m -> gametype == null || gametype.equals(m.getGametype().getGametype()))
                .map(m -> response(m, actor)).toList();
    }

    @Transactional(readOnly = true)
    public MatchResponse get(final long id, final String actor) {
        Match match = matches.findById(id).orElseThrow(() -> new GameNotFoundException(id));
        requireRead(match, actor);
        return response(match, actor);
    }

    @Transactional
    public MatchResponse start(final long id, final String actor) {
        Match match = locked(id);
        requireParticipant(match, actor);
        if (match.getStatus() == MatchStatus.PENDING) {
            match.setStatus(MatchStatus.IN_PROGRESS);
            match.setStartedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
            Leg leg = currentLeg(match);
            leg.setStatus(MatchStatus.IN_PROGRESS);
            leg.setStartedAt(match.getStartedAt());
            advanceRevision(match);
        } else if (match.getStatus() != MatchStatus.IN_PROGRESS) {
            throw new GameConflictException("This game has ended");
        }
        return response(match, actor);
    }

    @Transactional
    public MatchResponse play(final long id, final PlayedRoundRequest request, final String actor) {
        Match match = locked(id);
        requireParticipant(match, actor);
        if (match.getStatus() != MatchStatus.IN_PROGRESS) {
            throw new GameConflictException("The game is not in progress");
        }
        Leg leg = currentLeg(match);
        if (request.revision() != match.getRevision() || request.legId() != leg.getLegId()) {
            throw new GameConflictException("Game state changed. Reload before scoring again");
        }
        LegPlayerStat stat = next(leg);
        if (!stat.getUser().getUserName().equals(request.username())) {
            throw new InvalidTurnException();
        }
        CountdownRules.Result result = CountdownRules.evaluate(stat.getPoints(), request.score());
        stat.setTurn(stat.getTurn() + 1);
        stat.setPoints(result.remaining());
        stat.setTriple20s(stat.getTriple20s() + result.triples());
        stat.setBullseyes(stat.getBullseyes() + result.bullseyes());
        stat.setHighestScore(Math.max(stat.getHighestScore(), result.scored()));
        if (result.won()) {
            stat.setHighestCheckout(Math.max(stat.getHighestCheckout(), result.scored()));
            complete(match, leg, stat.getUser());
        }
        advanceRevision(match);
        return response(match, actor);
    }

    @Transactional
    public MatchResponse abandon(final long id, final String actor) {
        Match match = locked(id);
        requireManager(match, actor);
        if (match.getStatus() == MatchStatus.ABANDONED) {
            return response(match, actor);
        }
        if (match.getStatus() != MatchStatus.IN_PROGRESS) {
            throw new GameConflictException("Only an in-progress game can be abandoned");
        }
        match.setStatus(MatchStatus.ABANDONED);
        match.setFinishedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        Leg leg = currentLeg(match);
        leg.setStatus(MatchStatus.ABANDONED);
        leg.setFinishedAt(match.getFinishedAt());
        advanceRevision(match);
        return response(match, actor);
    }

    @Transactional
    public void delete(final long id, final String actor) {
        Match match = locked(id);
        requireManager(match, actor);
        if (match.getStatus() != MatchStatus.PENDING) {
            throw new GameConflictException(
                    "Only a pending game can be deleted; history is preserved");
        }
        matches.delete(match);
    }

    private void complete(final Match match, final Leg leg, final User winner) {
        leg.setWinner(winner);
        leg.setStatus(MatchStatus.COMPLETED);
        leg.setFinishedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        // Single-leg progression. Future formats create another leg instead of ending the match.
        match.setWinner(winner);
        match.setStatus(MatchStatus.COMPLETED);
        match.setFinishedAt(leg.getFinishedAt());
        statistics.completeSingleLegMatch(leg, startingPoints(match.getGametype().getGametype()));
    }

    private Match locked(final long id) {
        return matches.findForUpdate(id).orElseThrow(() -> new GameNotFoundException(id));
    }

    private Leg currentLeg(final Match match) {
        return match.getLegs().getLast();
    }

    private LegPlayerStat next(final Leg leg) {
        return leg.getPlayerStats().stream().min(Comparator.comparingInt(LegPlayerStat::getTurn)
                .thenComparingInt(LegPlayerStat::getPosition)).orElseThrow();
    }

    private void advanceRevision(final Match match) {
        match.setRevision(match.getRevision() + 1);
    }

    private boolean participant(final Match match, final String actor) {
        return match.getParticipants().stream()
                .anyMatch(p -> p.getUser().getUserName().equals(actor));
    }

    private boolean admin(final String actor) {
        return users.findRoleByUserName(actor).filter("ADMIN"::equals).isPresent();
    }

    private void requireParticipant(final Match match, final String actor) {
        if (!participant(match, actor)) {
            throw new AccessDeniedException();
        }
    }

    private void requireRead(final Match match, final String actor) {
        if (!participant(match, actor) && !admin(actor)) {
            throw new AccessDeniedException();
        }
    }

    private void requireManager(final Match match, final String actor) {
        if (!match.getOwner().getUserName().equals(actor) && !admin(actor)) {
            throw new AccessDeniedException();
        }
    }

    private int startingPoints(final String gametype) {
        return switch (gametype) {
            case Constants.GAMETYPE_301 -> Constants.TARGET_301;
            case Constants.GAMETYPE_501 -> Constants.TARGET_501;
            default -> throw new GameModeNotFoundException(gametype);
        };
    }

    private MatchResponse response(final Match match, final String actor) {
        Leg leg = currentLeg(match);
        boolean member = participant(match, actor);
        boolean manager = match.getOwner().getUserName().equals(actor) || admin(actor);
        boolean pending = match.getStatus() == MatchStatus.PENDING;
        boolean playing = match.getStatus() == MatchStatus.IN_PROGRESS;
        var players = leg.getPlayerStats().stream()
                .sorted(Comparator.comparingInt(LegPlayerStat::getPosition))
                .map(s -> new MatchResponse.Player(s.getUser().getUserName(), s.getPosition(),
                        s.getPoints(), s.getTurn(), s.getBullseyes(), s.getTriple20s(),
                        s.getHighestScore(), s.getHighestCheckout(), s.getCheckoutAccuracy(),
                        match.getLegs().stream().filter(l -> l.getWinner() != null
                                && l.getWinner().getUserId() == s.getUser().getUserId()).count()))
                .toList();
        var legs = match.getLegs().stream().map(l -> new MatchResponse.LegSummary(l.getLegId(),
                l.getLegNumber(), l.getStatus(), username(l.getWinner()))).toList();
        return new MatchResponse(match.getMatchId(), match.getOwner().getUserName(),
                match.getGametype().getGametype(),
                startingPoints(match.getGametype().getGametype()), match.getStatus(),
                match.getCreatedAt(), match.getStartedAt(), match.getFinishedAt(),
                username(match.getWinner()),
                pending || playing ? username(next(leg).getUser()) : null,
                match.getRevision(), leg.getLegId(), "SINGLE_LEG", players, legs,
                new MatchResponse.Permissions(member && pending, member && playing,
                        manager && pending, manager && playing));
    }

    private String username(final User user) {
        return user == null ? null : user.getUserName();
    }
}
