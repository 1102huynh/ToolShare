package com.toolshare.availability;

import com.toolshare.availability.application.Phase2ANoBookingConflictProvider;
import com.toolshare.availability.domain.TimeRange;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase2ANoBookingConflictProviderTest {

    @Test
    void provider_returns_no_conflicts() {
        Phase2ANoBookingConflictProvider provider = new Phase2ANoBookingConflictProvider();

        TimeRange requestedRange = new TimeRange(
                LocalDateTime.of(2026, 8, 20, 10, 0),
                LocalDateTime.of(2026, 8, 20, 12, 0)
        );

        assertTrue(provider.findConflictingBookingRanges(UUID.randomUUID(), requestedRange).isEmpty());
    }
}