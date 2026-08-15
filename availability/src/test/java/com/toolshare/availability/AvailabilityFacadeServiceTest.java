package com.toolshare.availability;

import com.toolshare.availability.application.AvailabilityCalculationService;
import com.toolshare.availability.application.AvailabilityFacadeService;
import com.toolshare.availability.application.Phase2ANoBookingConflictProvider;
import com.toolshare.availability.domain.AvailabilityCalendar;
import com.toolshare.availability.domain.AvailabilityRule;
import com.toolshare.availability.domain.BlockedPeriod;
import com.toolshare.availability.domain.MaintenanceWindow;
import com.toolshare.availability.domain.TimeRange;
import com.toolshare.availability.infrastructure.InMemoryAvailabilityCalendarSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvailabilityFacadeServiceTest {

    @Test
    void missing_calendar_returns_unavailable() {
        AvailabilityFacadeService facade = new AvailabilityFacadeService(
                new InMemoryAvailabilityCalendarSource(Map.of()),
                new AvailabilityCalculationService(),
                new Phase2ANoBookingConflictProvider()
        );

        boolean available = facade.isAvailable(UUID.randomUUID(), requestedRange());

        assertFalse(available);
    }

    @Test
    void existing_calendar_delegates_to_calculation_service() {
        UUID listingId = UUID.randomUUID();
        AvailabilityCalendar calendar = new AvailabilityCalendar(
                listingId,
                List.of(new AvailabilityRule(range(9, 17))),
                List.of(),
                List.of()
        );

        AvailabilityFacadeService facade = new AvailabilityFacadeService(
                new InMemoryAvailabilityCalendarSource(Map.of(listingId, calendar)),
                new AvailabilityCalculationService(),
                new Phase2ANoBookingConflictProvider()
        );

        boolean available = facade.isAvailable(listingId, range(10, 11));

        assertTrue(available);
    }

    @Test
    void blocked_period_makes_search_unavailable() {
        UUID listingId = UUID.randomUUID();
        AvailabilityCalendar calendar = new AvailabilityCalendar(
                listingId,
                List.of(new AvailabilityRule(range(9, 17))),
                List.of(new BlockedPeriod(range(10, 12))),
                List.of()
        );

        AvailabilityFacadeService facade = new AvailabilityFacadeService(
                new InMemoryAvailabilityCalendarSource(Map.of(listingId, calendar)),
                new AvailabilityCalculationService(),
                new Phase2ANoBookingConflictProvider()
        );

        boolean available = facade.isAvailable(listingId, range(10, 11));

        assertFalse(available);
    }

    @Test
    void maintenance_window_makes_search_unavailable() {
        UUID listingId = UUID.randomUUID();
        AvailabilityCalendar calendar = new AvailabilityCalendar(
                listingId,
                List.of(new AvailabilityRule(range(9, 17))),
                List.of(),
                List.of(new MaintenanceWindow(range(13, 14)))
        );

        AvailabilityFacadeService facade = new AvailabilityFacadeService(
                new InMemoryAvailabilityCalendarSource(Map.of(listingId, calendar)),
                new AvailabilityCalculationService(),
                new Phase2ANoBookingConflictProvider()
        );

        boolean available = facade.isAvailable(listingId, range(13, 14));

        assertFalse(available);
    }

    @Test
    void fully_available_range_is_returned_as_available() {
        UUID listingId = UUID.randomUUID();
        AvailabilityCalendar calendar = new AvailabilityCalendar(
                listingId,
                List.of(new AvailabilityRule(range(9, 17))),
                List.of(),
                List.of()
        );

        AvailabilityFacadeService facade = new AvailabilityFacadeService(
                new InMemoryAvailabilityCalendarSource(Map.of(listingId, calendar)),
                new AvailabilityCalculationService(),
                new Phase2ANoBookingConflictProvider()
        );

        boolean available = facade.isAvailable(listingId, range(9, 17));

        assertTrue(available);
    }

    private TimeRange requestedRange() {
        return range(10, 11);
    }

    private TimeRange range(int startHour, int endHour) {
        return new TimeRange(
                LocalDateTime.of(2026, 8, 20, startHour, 0),
                LocalDateTime.of(2026, 8, 20, endHour, 0)
        );
    }
}