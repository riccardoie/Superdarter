package lux.dartgame.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lux.dartgame.constants.Constants;
import lux.dartgame.dto.CreateGameRequest;
import lux.dartgame.dto.FriendResponse;
import lux.dartgame.dto.MatchResponse;
import lux.dartgame.dto.PlayedRoundRequest;
import lux.dartgame.dto.UserRequest;
import lux.dartgame.dto.UndoRoundRequest;
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
import lux.dartgame.model.PlayedRound;
import lux.dartgame.model.RoundSnapshot;
import lux.dartgame.model.User;
import lux.dartgame.repository.GametypeRepository;
import lux.dartgame.repository.MatchRepository;
import lux.dartgame.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.annotation.Transactional;

import static lux.dartgame.service.CountdownRules.startingPoints;

@Service
public class MatchService {
    private final MatchRepository matches;
    private final UserRepository users;
    private final GametypeRepository gametypes;
    private final MatchStatisticsService statistics;
    private final FriendService friends;

    public MatchService(final MatchRepository matchesParam, final UserRepository usersParam,
                        final GametypeRepository gametypesParam,
                        final MatchStatisticsService statisticsParam,
                        final FriendService friendsParam) {
        matches = matchesParam;
        users = usersParam;
        gametypes = gametypesParam;
        statistics = statisticsParam;
        friends = friendsParam;
    }

    @Transactional
    public MatchResponse create(final CreateGameRequest request, final String actor) {
        startingPoints(request.gametype());
        Match match = new Match();
        match.setLegsToWin(request.legsToWin());
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
        Set<String> accepted = names.size() > 1 ? acceptedFriends(actor) : Set.of();
        for (String name : names) {
            User user = users.findByUserName(name).orElseThrow(UsernameNotFoundException::new);
            if (!actor.equals(name) && !accepted.contains(name)) {
                throw new IllegalArgumentException(
                        "Only accepted friends can be added as registered players");
            }
            MatchParticipant participant = new MatchParticipant();
            participant.setMatch(match);
            participant.setUser(user);
            participant.setPosition(match.getParticipants().size());
            match.getParticipants().add(participant);
        }
        if (request.guests() != null) {
            for (String guest : request.guests()) {
                String name = guest.strip();
                if (name.isBlank() || !names.add(name)) {
                    throw new IllegalArgumentException(
                            "Each player needs a unique name in this game");
                }
                MatchParticipant participant = new MatchParticipant();
                participant.setMatch(match);
                participant.setGuestName(name);
                participant.setPosition(match.getParticipants().size());
                match.getParticipants().add(participant);
            }
        }
        if (match.getParticipants().size() > Constants.MAX_NR_OF_PLAYERS) {
            throw new IllegalArgumentException("Maximum 10 players including the owner and guests");
        }
        createLeg(match);
        matches.saveAndFlush(match);
        return response(match, actor);
    }

    @Transactional(readOnly = true)
    public List<MatchResponse> list(final String actor, final String gametype) {
        return matches.findForParticipant(actor).stream()
                .filter(m -> gametype == null || gametype.equals(m.getGametype().getGametype()))
                .map(m -> response(m, actor)).toList();
    }

    @Transactional
    public MatchResponse rematch(final long id, final String actor) {
        Match original = locked(id);
        requireParticipant(original, actor);
        if (original.getStatus() != MatchStatus.COMPLETED
                && original.getStatus() != MatchStatus.ABANDONED) {
            throw new GameConflictException("Finish the current game before starting a rematch");
        }
        Match match = new Match();
        match.setOwner(users.findByUserName(actor).orElseThrow(UsernameNotFoundException::new));
        match.setGametype(original.getGametype());
        match.setLegsToWin(original.getLegsToWin());
        Set<String> accepted = acceptedFriends(actor);
        for (MatchParticipant previous : original.getParticipants()) {
            if (previous.getUser() != null
                    && !previous.getUser().getUserName().equals(actor)
                    && !accepted.contains(previous.getUser().getUserName())) {
                throw new IllegalArgumentException(
                        "Only accepted friends can be added as registered players");
            }
            MatchParticipant participant = new MatchParticipant();
            participant.setMatch(match);
            participant.setUser(previous.getUser());
            participant.setGuestName(previous.getGuestName());
            participant.setPosition(previous.getPosition());
            match.getParticipants().add(participant);
        }
        match.setStatus(MatchStatus.IN_PROGRESS);
        match.setStartedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        createLeg(match);
        advanceRevision(match);
        matches.saveAndFlush(match);
        return response(match, actor);
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
        if (!playerName(stat).equals(request.username())) {
            throw new InvalidTurnException();
        }
        CountdownRules.Result result = CountdownRules.evaluate(stat.getPoints(), request.score(),
                Constants.FIRST_NINE_DARTS - stat.getFirstNineDarts(),
                doubleOut(match.getGametype().getGametype()));
        PlayedRound round = new PlayedRound();
        round.setMatch(match);
        round.setPlayerStat(stat);
        round.getBoardHits().putAll(result.boardHits());
        RoundSnapshot snapshot = new RoundSnapshot();
        BeanUtils.copyProperties(stat, snapshot);
        round.setBeforeRound(snapshot);
        match.getRounds().add(round);
        LegScoringStatistics.record(stat, result);
        stat.setTurn(stat.getTurn() + 1);
        stat.setPoints(result.remaining());
        stat.setTriple20s(stat.getTriple20s() + result.triples());
        stat.setBullseyes(stat.getBullseyes() + result.bullseyes());
        stat.setHighestScore(Math.max(stat.getHighestScore(), result.scored()));
        if (result.won()) {
            stat.setHighestCheckout(Math.max(stat.getHighestCheckout(), result.scored()));
            complete(match, leg, stat);
        }
        advanceRevision(match);
        // Persist a newly created next leg before exposing its generated ID to the client.
        matches.saveAndFlush(match);
        return response(match, actor);
    }

    @Transactional
    public MatchResponse undo(final long id, final UndoRoundRequest request, final String actor) {
        Match match = locked(id);
        requireParticipant(match, actor);
        if (request.revision() != match.getRevision()) {
            throw new GameConflictException("Game state changed. Reload before undoing a round");
        }
        if ((match.getStatus() != MatchStatus.IN_PROGRESS
                && match.getStatus() != MatchStatus.COMPLETED) || match.getRounds().isEmpty()) {
            throw new GameConflictException("There is no submitted round to undo");
        }
        PlayedRound round = match.getRounds().removeLast();
        LegPlayerStat stat = round.getPlayerStat();
        Leg leg = stat.getLeg();
        boolean completed = leg.getStatus() == MatchStatus.COMPLETED;
        // A checkout may have created a new, unplayed leg. Remove it when reopening that leg.
        match.getLegs().removeIf(l -> l.getLegNumber() > leg.getLegNumber());
        leg.setStatus(MatchStatus.IN_PROGRESS);
        leg.setWinner(null);
        leg.setWinnerGuestName(null);
        leg.setFinishedAt(null);
        match.setStatus(MatchStatus.IN_PROGRESS);
        match.setWinner(null);
        match.setWinnerGuestName(null);
        match.setFinishedAt(null);
        BeanUtils.copyProperties(round.getBeforeRound(), stat);
        advanceRevision(match);
        matches.saveAndFlush(match);
        if (completed) {
            statistics.rebuildTotals(leg);
        }
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
        MatchResponse result = response(match, actor);
        if (match.getLegs().stream().noneMatch(l -> l.getStatus() == MatchStatus.COMPLETED)) {
            match.getRounds().clear();
            matches.flush();
            matches.delete(match);
        }
        return result;
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

    private void complete(final Match match, final Leg leg, final LegPlayerStat winner) {
        leg.setWinner(winner.getUser());
        leg.setWinnerGuestName(winner.getGuestName());
        leg.setStatus(MatchStatus.COMPLETED);
        leg.setFinishedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        boolean matchCompleted = legsWon(match, playerName(winner)) >= match.getLegsToWin();
        if (matchCompleted) {
            match.setWinner(winner.getUser());
            match.setWinnerGuestName(winner.getGuestName());
            match.setStatus(MatchStatus.COMPLETED);
            match.setFinishedAt(leg.getFinishedAt());
        }
        statistics.completeLeg(leg,
                startingPoints(match.getGametype().getGametype()), matchCompleted);
        if (!matchCompleted) {
            createLeg(match);
        }
    }

    private long legsWon(final Match match, final String name) {
        return match.getLegs().stream().filter(l -> l.getStatus() == MatchStatus.COMPLETED
                && name.equals(winnerName(l.getWinner(), l.getWinnerGuestName()))).count();
    }

    private Set<String> acceptedFriends(final String actor) {
        return friends.friends(actor).stream().map(FriendResponse::username)
                .collect(Collectors.toSet());
    }

    private void createLeg(final Match match) {
        Leg leg = new Leg();
        leg.setMatch(match);
        leg.setLegNumber(match.getLegs().size() + 1);
        if (match.getStatus() == MatchStatus.IN_PROGRESS) {
            leg.setStatus(MatchStatus.IN_PROGRESS);
            leg.setStartedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        }
        List<MatchParticipant> participants = match.getParticipants();
        int offset = (leg.getLegNumber() - 1) % participants.size();
        for (int position = 0; position < participants.size(); position++) {
            var participant = participants.get((position + offset) % participants.size());
            LegPlayerStat stat = new LegPlayerStat();
            stat.setLeg(leg);
            stat.setUser(participant.getUser());
            stat.setGuestName(participant.getGuestName());
            stat.setPosition(position);
            stat.setPoints(startingPoints(match.getGametype().getGametype()));
            leg.getPlayerStats().add(stat);
        }
        match.getLegs().add(leg);
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
                .anyMatch(p -> p.getUser() != null && p.getUser().getUserName().equals(actor));
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

    private boolean doubleOut(final String gametype) {
        return switch (gametype) {
            case Constants.GAMETYPE_301, Constants.GAMETYPE_501 -> true;
            case Constants.GAMETYPE_301_SINGLE_OUT, Constants.GAMETYPE_501_SINGLE_OUT -> false;
            default -> throw new GameModeNotFoundException(gametype);
        };
    }

    private MatchResponse response(final Match match, final String actor) {
        Leg leg = currentLeg(match);
        boolean member = participant(match, actor);
        boolean manager = match.getOwner().getUserName().equals(actor) || admin(actor);
        boolean pending = match.getStatus() == MatchStatus.PENDING;
        boolean playing = match.getStatus() == MatchStatus.IN_PROGRESS;
        var players = players(match, leg);
        var legs = match.getLegs().stream().map(l -> new MatchResponse.LegSummary(
                l.getLegId(), l.getLegNumber(), l.getStatus(),
                winnerName(l.getWinner(), l.getWinnerGuestName()),
                players(match, l))).toList();
        return new MatchResponse(match.getMatchId(), match.getOwner().getUserName(),
                match.getGametype().getGametype(),
                startingPoints(match.getGametype().getGametype()), match.getStatus(),
                match.getCreatedAt(), match.getStartedAt(), match.getFinishedAt(),
                winnerName(match.getWinner(), match.getWinnerGuestName()),
                pending || playing ? playerName(next(leg)) : null,
                match.getRevision(), leg.getLegId(),
                match.getLegsToWin() == 1 ? "SINGLE_LEG" : "FIRST_TO_LEGS", players, legs,
                new MatchResponse.Permissions(member && pending, member && playing,
                        manager && pending, manager && playing,
                        member && (playing || match.getStatus() == MatchStatus.COMPLETED)
                                && !match.getRounds().isEmpty(),
                        member && (match.getStatus() == MatchStatus.COMPLETED
                                || match.getStatus() == MatchStatus.ABANDONED)),
                match.getLegsToWin());
    }

    private List<MatchResponse.Player> players(final Match match, final Leg leg) {
        return leg.getPlayerStats().stream()
                .sorted(Comparator.comparingInt(LegPlayerStat::getPosition))
                .map(s -> new MatchResponse.Player(playerName(s), s.getPosition(),
                        s.getPoints(), s.getTurn(), s.getBullseyes(), s.getTriple20s(),
                        s.getHighestScore(), s.getHighestCheckout(),
                        legsWon(match, playerName(s)), scoringStats(s), s.getUser() == null))
                .toList();
    }

    private MatchResponse.ScoringStats scoringStats(final LegPlayerStat stat) {
        if (!stat.isScoringStatsAvailable()) {
            return null;
        }
        float average = stat.getFirstNineDarts() == 0 ? 0f
                : (float) stat.getFirstNinePoints() * Constants.MAX_DARTS_PER_TURN
                / stat.getFirstNineDarts();
        return new MatchResponse.ScoringStats(stat.getDartsThrown(), stat.getFirstNinePoints(),
                stat.getFirstNineDarts(), average, stat.getScore40(), stat.getScore60(),
                stat.getScore80(), stat.getScore100(), stat.getScore120(), stat.getScore140(),
                stat.getScore160(), stat.getScore180());
    }

    private String playerName(final LegPlayerStat stat) {
        return winnerName(stat.getUser(), stat.getGuestName());
    }

    private String winnerName(final User user, final String guestName) {
        return user == null ? guestName : user.getUserName();
    }
}
