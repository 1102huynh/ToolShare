package com.toolshare.listing.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CategoryRequest(
        @NotBlank(message = "key is required")
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "key format is invalid")
        String key,

        @NotBlank(message = "displayName is required")
        String displayName
) {
}