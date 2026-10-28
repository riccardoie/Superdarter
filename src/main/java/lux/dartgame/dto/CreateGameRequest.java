package lux.dartgame.dto;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lux.dartgame.constants.Constants;

public record CreateGameRequest(@NotBlank String gametype,
                               @Size(max = Constants.MAX_NR_OF_PLAYERS)
                               List<@NotNull @Valid UserRequest> players,
                               @Min(1) @Max(Constants.MAX_LEGS_TO_WIN) Integer legsToWin,
                               @Size(max = Constants.MAX_NR_OF_PLAYERS)
                               List<@NotBlank @Size(max = Constants.MAX_PLAYER_NAME_LENGTH)
                                       String> guests) {
    public CreateGameRequest {
        if (legsToWin == null) {
            legsToWin = 1;
        }
    }

    public CreateGameRequest(final String gametypeParam, final List<UserRequest> playersParam) {
        this(gametypeParam, playersParam, 1, null);
    }

    public CreateGameRequest(final String gametypeParam, final List<UserRequest> playersParam,
                             final Integer legsToWinParam) {
        this(gametypeParam, playersParam, legsToWinParam, null);
    }
}
