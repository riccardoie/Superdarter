package lux.dartgame.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

public record PlayedRoundRequest(@NotBlank String username, @NotNull String score,
                                 @NotNull @Positive Long legId,
                                  @NotNull @PositiveOrZero Long revision,
                                  List<String> allocations) {
    public PlayedRoundRequest(final String usernameParam, final String scoreParam,
                              final Long legIdParam, final Long revisionParam) {
        this(usernameParam, scoreParam, legIdParam, revisionParam, null);
    }
}
