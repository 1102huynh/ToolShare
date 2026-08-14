package com.toolshare.listing.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ListingImageResponse(
        UUID id,
        String storageKey,
        int displayOrder,
        OffsetDateTime createdAt
) {
}