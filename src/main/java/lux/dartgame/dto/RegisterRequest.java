package lux.dartgame.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lux.dartgame.constants.Constants;

public record RegisterRequest(
        @NotBlank
        @Pattern(regexp = "\\S+", message = "Username must not contain spaces or whitespace")
        String username,
        @NotBlank
        @Size(min = Constants.MIN_LENGTH_PWD,
              message = "Password must be at least 8 characters long")
        @Pattern(regexp = "[\\s\\S]*[0-9][\\s\\S]*",
                message = "Password must contain at least one number")
        String password,
        @NotBlank @Email String email) {
}
