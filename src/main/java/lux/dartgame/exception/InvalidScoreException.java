package lux.dartgame.exception;

public class InvalidScoreException extends IllegalArgumentException {
    public InvalidScoreException() {
        super("Invalid darts: use up to three valid number/multiplier pairs");
    }
}
