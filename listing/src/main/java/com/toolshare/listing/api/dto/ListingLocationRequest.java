package com.toolshare.listing.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ListingLocationRequest(
        @NotBlank(message = "addressLine1 is required")
        String addressLine1,

        String addressLine2,

        @NotBlank(message = "ward is required")
        String ward,

        @NotBlank(message = "district is required")
        String district,

        @NotBlank(message = "city is required")
        String city,

        @NotBlank(message = "countryCode is required")
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "countryCode must be a 2-letter code")
        String countryCode
) {
}