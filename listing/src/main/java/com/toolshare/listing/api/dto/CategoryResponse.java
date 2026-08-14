package com.toolshare.listing.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String key,
        String displayName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}