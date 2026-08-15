package com.toolshare.search.application;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SearchListingSummary(
        UUID listingId,
        String title,
        String description,
        UUID categoryId,
        String categoryKey,
        String categoryDisplayName,
        String city,
        String district,
        long dailyRateAmount,
        long depositAmount,
        String currency,
        boolean deliveryAvailable,
        OffsetDateTime createdAt
) {
}