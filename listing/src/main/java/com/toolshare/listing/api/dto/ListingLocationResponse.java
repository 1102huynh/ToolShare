package com.toolshare.listing.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ListingLocationResponse(
        UUID id,
        String addressLine1,
        String addressLine2,
        String ward,
        String district,
        String city,
        String countryCode,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}