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
@Table(name = "leg_player_stats")
@Getter
@Setter
public class LegPlayerStat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long statId;
    @ManyToOne
    @JoinColumn(name = "leg_id", nullable = false)
    private Leg leg;
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    private int position;
    private int points;
    private int turn;
    private int triple20s;
    private int bullseyes;
    private int highestScore;
    private int highestCheckout;
    private float checkoutAccuracy;
    private boolean scoringStatsAvailable = true;
    private int dartsThrown;
    private int firstNinePoints;
    private int firstNineDarts;
    private int score40;
    private int score60;
    private int score80;
    private int score100;
    private int score120;
    private int score140;
    private int score160;
    private int score180;
}
