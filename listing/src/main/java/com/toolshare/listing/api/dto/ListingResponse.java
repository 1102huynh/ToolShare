package com.toolshare.listing.api.dto;

import com.toolshare.listing.domain.ListingStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ListingResponse(
        UUID id,
        UUID ownerAccountId,
        UUID categoryId,
        ListingStatus status,
        String title,
        String description,
        long dailyRateAmount,
        long depositAmount,
        String currency,
        boolean deliveryAvailable,
        ListingLocationResponse location,
        List<ListingImageResponse> images,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}