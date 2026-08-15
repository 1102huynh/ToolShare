package com.toolshare.availability.application;

import com.toolshare.availability.domain.TimeRange;

import java.util.UUID;

public interface AvailabilityFacade {
    boolean isAvailable(UUID listingId, TimeRange requestedRange);
}