package lux.dartgame.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record BullOffDecisionRequest(@NotNull @Positive Long legId,
                                     @NotNull @PositiveOrZero Long revision,
                                     String winner, boolean rethrow) {
}
