package lux.dartgame.exception;

public class FriendRequestNotFoundException extends RuntimeException {
    public FriendRequestNotFoundException() {
        super("Friend request not found");
    }
}
