package lux.dartgame.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UndoRoundRequest(@NotNull @PositiveOrZero Long revision) {
}
