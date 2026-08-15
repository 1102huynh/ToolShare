package com.toolshare.availability;

import com.toolshare.availability.domain.AvailabilityCalendar;
import com.toolshare.availability.domain.AvailabilityRule;
import com.toolshare.availability.domain.TimeRange;
import com.toolshare.availability.infrastructure.InMemoryAvailabilityCalendarSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryAvailabilityCalendarSourceTest {

    @Test
    void source_stores_and_retrieves_calendar_by_listing_id() {
        UUID listingId = UUID.randomUUID();
        AvailabilityCalendar calendar = new AvailabilityCalendar(
                listingId,
                List.of(new AvailabilityRule(new TimeRange(
                        LocalDateTime.of(2026, 8, 20, 9, 0),
                        LocalDateTime.of(2026, 8, 20, 17, 0)
                ))),
                List.of(),
                List.of()
        );

        InMemoryAvailabilityCalendarSource source = new InMemoryAvailabilityCalendarSource(Map.of(listingId, calendar));

        assertTrue(source.findByListingId(listingId).isPresent());
    }
}