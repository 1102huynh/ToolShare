package com.toolshare.listing.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.UUID;

public record ListingRequest(
        @NotNull(message = "categoryId is required")
        UUID categoryId,

        @Valid
        @NotNull(message = "location is required")
        ListingLocationRequest location,

        @NotBlank(message = "title is required")
        String title,

        @NotBlank(message = "description is required")
        String description,

        @Min(value = 0, message = "dailyRateAmount must be zero or greater")
        long dailyRateAmount,

        @Min(value = 0, message = "depositAmount must be zero or greater")
        long depositAmount,

        @NotBlank(message = "currency is required")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "currency must be a 3-letter code")
        String currency,

        boolean deliveryAvailable,

        @Valid
        List<ListingImageRequest> images
) {
}