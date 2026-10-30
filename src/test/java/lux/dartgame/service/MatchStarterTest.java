package lux.dartgame.service;

import java.util.List;
import java.util.Optional;
import lux.dartgame.dto.CreateGameRequest;
import lux.dartgame.dto.MatchResponse;
import lux.dartgame.dto.PlayedRoundRequest;
import lux.dartgame.dto.UndoRoundRequest;
import lux.dartgame.model.Gametype;
import lux.dartgame.model.Match;
import lux.dartgame.model.MatchStatus;
import lux.dartgame.model.User;
import lux.dartgame.repository.GametypeRepository;
import lux.dartgame.repository.MatchRepository;
import lux.dartgame.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchStarterTest {
    @Mock private MatchRepository matches;
    @Mock private UserRepository users;
    @Mock private GametypeRepository gametypes;
    @Mock private MatchStatisticsService statistics;
    @Mock private FriendService friends;
    @Mock private StartingPlayerSelector starters;
    @InjectMocks private MatchService service;

    @BeforeEach
    void setup() {
        User owner = new User();
        owner.setUserName("alice");
        Gametype type = new Gametype();
        type.setGametype("301");
        when(users.findByUserName("alice")).thenReturn(Optional.of(owner));
        when(gametypes.findByGametype("301")).thenReturn(Optional.of(type));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void selectedStarterRotatesWrapsAndSurvivesUndoWithoutAnotherDraw(final int offset) {
        when(starters.choose(3)).thenReturn(offset);
        List<String> names = List.of("alice", "Guest A", "Guest B");
        MatchResponse game = service.create(new CreateGameRequest("301", List.of(), 5,
                List.of("Guest A", "Guest B")), "alice");
        var captured = ArgumentCaptor.forClass(Match.class);
        verify(matches).saveAndFlush(captured.capture());
        Match match = captured.getValue();
        assertThat(match.getStartingPlayerOffset()).isEqualTo(offset);
        assertThat(match.getParticipants()).extracting(p -> p.getPosition()).containsExactly(0, 1, 2);
        assertThat(game.owner()).isEqualTo("alice");
        assertThat(game.turn()).isEqualTo(names.get(offset));
        when(matches.findForUpdate(0)).thenReturn(Optional.of(match));
        when(matches.findById(0L)).thenReturn(Optional.of(match));
        game = service.start(0, "alice");
        for (int leg = 0; leg < 3; leg++) {
            String starter = names.get((offset + leg) % names.size());
            assertThat(game.turn()).isEqualTo(starter);
            assertThat(service.get(0, "alice").turn()).isEqualTo(starter);
            match.getLegs().getLast().getPlayerStats().forEach(player -> player.setPoints(2));
            game = checkout(game);
            String next = names.get((offset + leg + 1) % names.size());
            assertThat(game.turn()).isEqualTo(next);
            game = service.undo(0, new UndoRoundRequest(game.revision()), "alice");
            assertThat(game.turn()).isEqualTo(starter);
            game = checkout(game);
            assertThat(game.turn()).isEqualTo(next);
        }
        verify(starters, times(1)).choose(3);
    }

    @Test
    void rematchDrawsANewStarterWithoutChangingParticipantOrder() {
        when(starters.choose(3)).thenReturn(1, 2);
        service.create(new CreateGameRequest("301", List.of(), 1,
                List.of("Guest A", "Guest B")), "alice");
        var captured = ArgumentCaptor.forClass(Match.class);
        verify(matches).saveAndFlush(captured.capture());
        Match original = captured.getValue();
        original.setStatus(MatchStatus.COMPLETED);
        when(matches.findForUpdate(0)).thenReturn(Optional.of(original));
        MatchResponse rematch = service.rematch(0, "alice");
        assertThat(rematch.turn()).isEqualTo("Guest B");
        assertThat(rematch.players()).extracting(MatchResponse.Player::username)
                .containsExactly("Guest B", "alice", "Guest A");
        assertThat(original.getStartingPlayerOffset()).isEqualTo(1);
        verify(starters, times(2)).choose(3);
    }

    @Test
    void soloGamesStillStartWithTheirOnlyPlayer() {
        MatchResponse game = service.create(new CreateGameRequest("301", List.of(), 1), "alice");
        assertThat(game.turn()).isEqualTo("alice");
        verify(starters).choose(1);
    }

    private MatchResponse checkout(final MatchResponse game) {
        return service.play(0, new PlayedRoundRequest(game.turn(), "1 2", game.currentLegId(),
                game.revision()), "alice");
    }
}
