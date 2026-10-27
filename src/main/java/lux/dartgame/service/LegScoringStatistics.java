package lux.dartgame.service;

import lux.dartgame.constants.Constants;
import lux.dartgame.model.LegPlayerStat;

/** Detailed counters for a leg; old, partially recorded legs stay unavailable. */
public final class LegScoringStatistics {
    private static final int SCORE_40 = 40;
    private static final int SCORE_60 = 60;
    private static final int SCORE_80 = 80;
    private static final int SCORE_100 = 100;
    private static final int SCORE_120 = 120;
    private static final int SCORE_140 = 140;
    private static final int SCORE_160 = 160;

    private LegScoringStatistics() {
    }

    public static void record(final LegPlayerStat stat, final CountdownRules.Result result) {
        if (!stat.isScoringStatsAvailable()) {
            return;
        }
        stat.setDartsThrown(stat.getDartsThrown() + result.dartsThrown());
        stat.setFirstNinePoints(stat.getFirstNinePoints() + result.firstNinePoints());
        stat.setFirstNineDarts(stat.getFirstNineDarts() + result.firstNineDarts());
        // Exclusive bands: e.g. 100-119 increments only score100. Busts score zero.
        switch (result.scored() / Constants.SCORE_BAND_WIDTH * Constants.SCORE_BAND_WIDTH) {
            case SCORE_40 -> stat.setScore40(stat.getScore40() + 1);
            case SCORE_60 -> stat.setScore60(stat.getScore60() + 1);
            case SCORE_80 -> stat.setScore80(stat.getScore80() + 1);
            case SCORE_100 -> stat.setScore100(stat.getScore100() + 1);
            case SCORE_120 -> stat.setScore120(stat.getScore120() + 1);
            case SCORE_140 -> stat.setScore140(stat.getScore140() + 1);
            case SCORE_160 -> stat.setScore160(stat.getScore160() + 1);
            case Constants.MAX_ROUND_SCORE -> stat.setScore180(stat.getScore180() + 1);
            default -> { /* Scores below 40 have no displayed band. */ }
        }
    }
}
