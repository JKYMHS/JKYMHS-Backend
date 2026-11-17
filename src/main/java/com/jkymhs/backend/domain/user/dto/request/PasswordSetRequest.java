package com.jkymhs.backend.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordSetRequest(
        @NotBlank(message = PasswordRule.MESSAGE)
        @Pattern(regexp = PasswordRule.REGEX, message = PasswordRule.MESSAGE)
        String newPassword
) {
}
