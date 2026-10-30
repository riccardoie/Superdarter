package lux.dartgame.model;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import lombok.Getter;
import lombok.Setter;

/** Player state immediately before a submitted round; excludes identity and relationships. */
@Embeddable
@Getter
@Setter
public class RoundSnapshot {
    private int points;
    @Embedded
    private CricketMarks cricketMarks = new CricketMarks();
    private int turn;
    private int triple20s;
    private int bullseyes;
    private int highestScore;
    private int highestCheckout;
    private boolean scoringStatsAvailable;
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
