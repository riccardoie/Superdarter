package lux.dartgame.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "matches")
@Getter
@Setter
public class Match {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long matchId;
    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;
    @ManyToOne
    @JoinColumn(name = "gametype_id", nullable = false)
    private Gametype gametype;
    @Enumerated(EnumType.STRING)
    private MatchStatus status = MatchStatus.PENDING;
    private Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private Instant startedAt;
    private Instant finishedAt;
    @ManyToOne
    @JoinColumn(name = "winner_id")
    private User winner;
    private String winnerGuestName;
    // Checked under a database row lock for every command; prevents stale/replayed rounds.
    private long revision;
    private int legsToWin = 1;
    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<MatchParticipant> participants = new ArrayList<>();
    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("legNumber ASC")
    private List<Leg> legs = new ArrayList<>();
    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("roundId ASC")
    private List<PlayedRound> rounds = new ArrayList<>();
}
