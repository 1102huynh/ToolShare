package com.toolshare.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(
        @NotBlank(message = "verificationToken is required")
        String verificationToken
) {
}
