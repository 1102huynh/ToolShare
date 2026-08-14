package com.toolshare.listing.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ListingImageRequest(
        @NotBlank(message = "storageKey is required")
        String storageKey,

        @Min(value = 0, message = "displayOrder must be zero or greater")
        int displayOrder
) {
}