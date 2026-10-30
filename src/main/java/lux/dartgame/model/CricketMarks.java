package lux.dartgame.model;

import java.util.Map;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter
@Setter
public class CricketMarks {
    private static final int TARGET_15 = 15;
    private static final int TARGET_16 = 16;
    private static final int TARGET_17 = 17;
    private static final int TARGET_18 = 18;
    private static final int TARGET_19 = 19;
    private static final int TARGET_20 = 20;
    private static final int TARGET_BULL = 25;
    @Column(name = "cricket15")
    private int fifteen;
    @Column(name = "cricket16")
    private int sixteen;
    @Column(name = "cricket17")
    private int seventeen;
    @Column(name = "cricket18")
    private int eighteen;
    @Column(name = "cricket19")
    private int nineteen;
    @Column(name = "cricket20")
    private int twenty;
    @Column(name = "cricket25")
    private int bull;

    public Map<Integer, Integer> asMap() {
        return Map.of(TARGET_15, fifteen, TARGET_16, sixteen, TARGET_17, seventeen,
                TARGET_18, eighteen, TARGET_19, nineteen, TARGET_20, twenty, TARGET_BULL, bull);
    }

    public static CricketMarks from(final Map<Integer, Integer> marks) {
        CricketMarks result = new CricketMarks();
        result.fifteen = marks.getOrDefault(TARGET_15, 0);
        result.sixteen = marks.getOrDefault(TARGET_16, 0);
        result.seventeen = marks.getOrDefault(TARGET_17, 0);
        result.eighteen = marks.getOrDefault(TARGET_18, 0);
        result.nineteen = marks.getOrDefault(TARGET_19, 0);
        result.twenty = marks.getOrDefault(TARGET_20, 0);
        result.bull = marks.getOrDefault(TARGET_BULL, 0);
        return result;
    }
}
