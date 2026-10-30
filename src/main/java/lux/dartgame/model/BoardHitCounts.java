package lux.dartgame.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lux.dartgame.constants.Constants;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BoardHitCounts {
    private int singleHits;
    private int doubleHits;
    private int tripleHits;

    public void record(final int multiplier) {
        switch (multiplier) {
            case 1 -> singleHits++;
            case 2 -> doubleHits++;
            case Constants.MAX_MULTIPLIER -> tripleHits++;
            default -> throw new IllegalArgumentException("Invalid multiplier");
        }
    }

    public int getTotal() {
        return singleHits + doubleHits + tripleHits;
    }
}
