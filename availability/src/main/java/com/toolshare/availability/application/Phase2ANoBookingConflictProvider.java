package com.toolshare.availability.application;

import com.toolshare.availability.domain.TimeRange;

import java.util.List;
import java.util.UUID;

public final class Phase2ANoBookingConflictProvider implements BookingConflictProvider {
    @Override
    public List<TimeRange> findConflictingBookingRanges(UUID listingId, TimeRange requestedRange) {
        // Phase 2A assumption: T-009 booking state is not implemented yet.
        // This default is intentionally replaceable by a future booking-backed implementation.
        return List.of();
    }
}