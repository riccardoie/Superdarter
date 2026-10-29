package lux.dartgame.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lux.dartgame.constants.Constants;

public record SendFriendRequest(
        @NotBlank @Size(max = Constants.MAX_PLAYER_NAME_LENGTH) String username) {
}
