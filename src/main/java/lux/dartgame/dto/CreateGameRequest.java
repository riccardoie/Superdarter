package lux.dartgame.dto;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lux.dartgame.constants.Constants;

public record CreateGameRequest(@NotBlank String gametype,
                               @Size(max = Constants.MAX_NR_OF_PLAYERS)
                               List<@NotNull @Valid UserRequest> players) {
}
