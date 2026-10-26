package lux.dartgame.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record PlayedRoundRequest(@NotBlank String username, @NotNull String score,
                                 @NotNull @Positive Long legId,
                                 @NotNull @PositiveOrZero Long revision) {
}
