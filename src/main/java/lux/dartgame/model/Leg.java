package lux.dartgame.model;

import java.time.Instant;
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
@Table(name = "legs")
@Getter
@Setter
public class Leg {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long legId;
    @ManyToOne
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;
    private int legNumber;
    @Enumerated(EnumType.STRING)
    private MatchStatus status = MatchStatus.PENDING;
    private Instant startedAt;
    private Instant finishedAt;
    @ManyToOne
    @JoinColumn(name = "winner_id")
    private User winner;
    @OneToMany(mappedBy = "leg", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<LegPlayerStat> playerStats = new ArrayList<>();
}
