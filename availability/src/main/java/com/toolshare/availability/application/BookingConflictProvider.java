package com.toolshare.availability.application;

import com.toolshare.availability.domain.TimeRange;

import java.util.List;
import java.util.UUID;

public interface BookingConflictProvider {
    List<TimeRange> findConflictingBookingRanges(UUID listingId, TimeRange requestedRange);
}