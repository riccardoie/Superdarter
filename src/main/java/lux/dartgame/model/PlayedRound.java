package lux.dartgame.model;

import jakarta.persistence.Embedded;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "played_rounds")
@Getter
@Setter
public class PlayedRound {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long roundId;
    @ManyToOne
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;
    @ManyToOne
    @JoinColumn(name = "stat_id", nullable = false)
    private LegPlayerStat playerStat;
    @Embedded
    private RoundSnapshot beforeRound;
    @ElementCollection
    @CollectionTable(name = "round_board_hits", joinColumns = @JoinColumn(name = "round_id"))
    @MapKeyColumn(name = "board_number")
    private Map<Integer, BoardHitCounts> boardHits = new HashMap<>();
}
