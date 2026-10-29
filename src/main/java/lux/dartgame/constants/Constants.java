package lux.dartgame.constants;

import java.util.Arrays;

public final class Constants {
    public static final int SECONDS_PER_MINUTE = 60;
    public static final int MAX_NR_OF_PLAYERS = 10;
    public static final int MAX_PLAYER_NAME_LENGTH = 255;
    public static final int MAX_FRIEND_SUGGESTIONS = 3;
    public static final int MIN_LENGTH_PWD = 8;

    public static final int BULLSEYE_VALUE = 25;
    public static final int MAX_DART_NUMBER = 20;
    public static final int MAX_MULTIPLIER = 3;
    public static final int MAX_ROUND_SCORE = 180;
    public static final int MAX_DARTS_PER_TURN = 3;
    public static final int FIRST_NINE_DARTS = 9;
    public static final int SCORE_BAND_WIDTH = 20;
    public static final int MAX_LEGS_TO_WIN = 99;
    public static final int TARGET_301 = 301;
    public static final int TARGET_501 = 501;
    public static final String GAMETYPE_301 = "301";
    public static final String GAMETYPE_501 = "501";
    public static final String GAMETYPE_301_SINGLE_OUT = "301 Single Out";
    public static final String GAMETYPE_501_SINGLE_OUT = "501 Single Out";

    private static final int[] IMPOSSIBLE_SCORES =
            {163, 166, 169, 172, 173, 175, 176, 178, 179};

    private Constants() {
    }

    public static boolean isImpossibleScore(final int score) {
        return Arrays.stream(IMPOSSIBLE_SCORES).anyMatch(i -> i == score);
    }
}
