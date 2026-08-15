package com.toolshare.search;

import com.toolshare.availability.application.AvailabilityCalculationService;
import com.toolshare.availability.application.AvailabilityFacadeService;
import com.toolshare.availability.application.Phase2ANoBookingConflictProvider;
import com.toolshare.availability.domain.AvailabilityCalendar;
import com.toolshare.availability.domain.AvailabilityRule;
import com.toolshare.availability.domain.BlockedPeriod;
import com.toolshare.availability.domain.MaintenanceWindow;
import com.toolshare.availability.domain.TimeRange;
import com.toolshare.availability.infrastructure.InMemoryAvailabilityCalendarSource;
import com.toolshare.search.application.ListingSearchPort;
import com.toolshare.search.application.SearchListingSummary;
import com.toolshare.search.application.SearchRequest;
import com.toolshare.search.application.SearchResult;
import com.toolshare.search.application.SearchService;
import com.toolshare.search.application.SearchSort;
import com.toolshare.search.infrastructure.AvailabilityProviderAdapter;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchServiceAvailabilityIntegrationTest {

    @Test
    void search_filters_availability_with_real_adapter_and_in_memory_source() {
        UUID availableListingId = UUID.randomUUID();
        UUID blockedListingId = UUID.randomUUID();

        AvailabilityCalendar availableCalendar = new AvailabilityCalendar(
                availableListingId,
                List.of(new AvailabilityRule(range(9, 17))),
                List.of(),
                List.of()
        );
        AvailabilityCalendar blockedCalendar = new AvailabilityCalendar(
                blockedListingId,
                List.of(new AvailabilityRule(range(9, 17))),
                List.of(new BlockedPeriod(range(10, 12))),
                List.of()
        );

        AvailabilityFacadeService availabilityFacade = new AvailabilityFacadeService(
                new InMemoryAvailabilityCalendarSource(Map.of(
                        availableListingId, availableCalendar,
                        blockedListingId, blockedCalendar
                )),
                new AvailabilityCalculationService(),
                new Phase2ANoBookingConflictProvider()
        );

        ListingSearchPort listingSearchPort = () -> List.of(
                summary(availableListingId, "Available Drill"),
                summary(blockedListingId, "Blocked Drill")
        );

        SearchService service = new SearchService(listingSearchPort, new AvailabilityProviderAdapter(availabilityFacade));

        SearchResult result = service.search(new SearchRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                range(10, 11),
                null,
                0,
                20,
                SearchSort.NEWEST
        ));

        assertEquals(1, result.items().size());
        assertEquals(availableListingId, result.items().get(0).listingId());
    }

    @Test
    void maintenance_window_makes_search_result_unavailable() {
        UUID listingId = UUID.randomUUID();
        AvailabilityCalendar calendar = new AvailabilityCalendar(
                listingId,
                List.of(new AvailabilityRule(range(9, 17))),
                List.of(),
                List.of(new MaintenanceWindow(range(10, 11)))
        );

        AvailabilityFacadeService availabilityFacade = new AvailabilityFacadeService(
                new InMemoryAvailabilityCalendarSource(Map.of(listingId, calendar)),
                new AvailabilityCalculationService(),
                new Phase2ANoBookingConflictProvider()
        );

        ListingSearchPort listingSearchPort = () -> List.of(summary(listingId, "Maintenance Drill"));
        SearchService service = new SearchService(listingSearchPort, new AvailabilityProviderAdapter(availabilityFacade));

        SearchResult result = service.search(new SearchRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                range(10, 11),
                null,
                0,
                20,
                SearchSort.NEWEST
        ));

        assertTrue(result.items().isEmpty());
    }

    private SearchListingSummary summary(UUID listingId, String title) {
        return new SearchListingSummary(
                listingId,
                title,
                "Description",
                UUID.randomUUID(),
                "drills",
                "Drills",
                "Ho Chi Minh City",
                "District 1",
                150_000L,
                500_000L,
                "VND",
                true,
                OffsetDateTime.of(LocalDateTime.of(2026, 8, 15, 10, 0), ZoneOffset.UTC)
        );
    }

    private TimeRange range(int startHour, int endHour) {
        return new TimeRange(
                LocalDateTime.of(2026, 8, 20, startHour, 0),
                LocalDateTime.of(2026, 8, 20, endHour, 0)
        );
    }
}