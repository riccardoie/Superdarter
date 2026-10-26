package lux.dartgame.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "match_participants")
@Getter
@Setter
public class MatchParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long participantId;
    @ManyToOne
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    private int position;
}
