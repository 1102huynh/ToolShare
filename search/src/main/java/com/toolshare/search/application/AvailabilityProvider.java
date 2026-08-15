package com.toolshare.search.application;

import com.toolshare.availability.domain.TimeRange;

import java.util.UUID;

public interface AvailabilityProvider {
    boolean isAvailable(UUID listingId, TimeRange requestedRange);
}