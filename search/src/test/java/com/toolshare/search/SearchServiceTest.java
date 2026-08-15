package com.toolshare.search;

import com.toolshare.availability.domain.TimeRange;
import com.toolshare.search.application.AvailabilityProvider;
import com.toolshare.search.application.ListingSearchPort;
import com.toolshare.search.application.SearchListingSummary;
import com.toolshare.search.application.SearchRequest;
import com.toolshare.search.application.SearchResult;
import com.toolshare.search.application.SearchService;
import com.toolshare.search.application.SearchSort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchServiceTest {

    private FakeListingSearchPort listingSearchPort;
    private RecordingAvailabilityProvider availabilityProvider;
    private SearchService service;

    @BeforeEach
    void setUp() {
        listingSearchPort = new FakeListingSearchPort();
        availabilityProvider = new RecordingAvailabilityProvider();
        service = new SearchService(listingSearchPort, availabilityProvider);
    }

    @Test
    void keyword_filtering_uses_searchable_fields() {
        listingSearchPort.setListings(baseListings());

        SearchResult result = service.search(requestBuilder().keyword("Hammer").build());

        assertEquals(1, result.items().size());
        assertEquals("Hammer Drill", result.items().get(0).title());
    }

    @Test
    void category_filtering_works() {
        listingSearchPort.setListings(baseListings());

        SearchResult result = service.search(requestBuilder().categoryId(categoryId("drills")).build());

        assertEquals(1, result.items().size());
        assertEquals(categoryId("drills"), result.items().get(0).categoryId());
    }

    @Test
    void city_filtering_works() {
        listingSearchPort.setListings(baseListings());

        SearchResult result = service.search(requestBuilder().city("Ho Chi Minh City").build());

        assertEquals(2, result.items().size());
        assertTrue(result.items().stream().allMatch(item -> item.city().equals("Ho Chi Minh City")));
    }

    @Test
    void district_filtering_works() {
        listingSearchPort.setListings(baseListings());

        SearchResult result = service.search(requestBuilder().district("District 1").build());

        assertEquals(1, result.items().size());
        assertEquals("District 1", result.items().get(0).district());
    }

    @Test
    void minimum_price_is_inclusive() {
        listingSearchPort.setListings(baseListings());

        SearchResult result = service.search(requestBuilder().minimumPrice(150_000L).build());

        assertEquals(2, result.items().size());
    }

    @Test
    void maximum_price_is_inclusive() {
        listingSearchPort.setListings(baseListings());

        SearchResult result = service.search(requestBuilder().maximumPrice(150_000L).build());

        assertEquals(2, result.items().size());
    }

    @Test
    void price_range_is_inclusive() {
        listingSearchPort.setListings(baseListings());

        SearchResult result = service.search(requestBuilder().minimumPrice(120_000L).maximumPrice(180_000L).build());

        assertEquals(1, result.items().size());
        assertEquals(150_000L, result.items().get(0).dailyRateAmount());
    }

    @Test
    void delivery_filtering_works() {
        listingSearchPort.setListings(baseListings());

        SearchResult result = service.search(requestBuilder().deliveryAvailable(true).build());

        assertEquals(2, result.items().size());
        assertTrue(result.items().stream().allMatch(SearchListingSummary::deliveryAvailable));
    }

    @Test
    void availability_filtering_uses_provider() {
        listingSearchPort.setListings(baseListings());
        availabilityProvider.allowOnly(listingId("drill"));

        SearchResult result = service.search(requestBuilder().requestedAvailabilityRange(range()).build());

        assertEquals(1, result.items().size());
        assertEquals(listingId("drill"), result.items().get(0).listingId());
    }

    @Test
    void unavailable_listing_is_excluded() {
        listingSearchPort.setListings(baseListings());
        availabilityProvider.allowOnly(listingId("drill"));

        SearchResult result = service.search(requestBuilder().requestedAvailabilityRange(range()).build());

        assertFalse(result.items().stream().anyMatch(item -> item.listingId().equals(listingId("saw"))));
    }

    @Test
    void pagination_works_after_filtering() {
        listingSearchPort.setListings(baseListings());

        SearchResult result = service.search(requestBuilder().pageNumber(0).pageSize(1).build());

        assertEquals(1, result.items().size());
        assertEquals(3, result.page().totalElements());
        assertEquals(3, result.page().totalPages());
        assertTrue(result.page().hasNext());
        assertFalse(result.page().hasPrevious());
    }

    @Test
    void sorting_newest_works() {
        listingSearchPort.setListings(baseListings());

        SearchResult result = service.search(requestBuilder().sort(SearchSort.NEWEST).build());

        assertEquals("Hammer Drill", result.items().get(0).title());
    }

    @Test
    void sorting_price_ascending_works() {
        listingSearchPort.setListings(baseListings());

        SearchResult result = service.search(requestBuilder().sort(SearchSort.PRICE_ASC).build());

        assertEquals(100_000L, result.items().get(0).dailyRateAmount());
    }

    @Test
    void multiple_filters_combined_work() {
        listingSearchPort.setListings(baseListings());
        availabilityProvider.allowOnly(listingId("drill"));

        SearchResult result = service.search(requestBuilder()
                .keyword("drill")
                .city("Ho Chi Minh City")
                .minimumPrice(120_000L)
                .maximumPrice(180_000L)
                .deliveryAvailable(true)
                .requestedAvailabilityRange(range())
                .build());

        assertEquals(1, result.items().size());
        assertEquals(listingId("drill"), result.items().get(0).listingId());
    }

    @Test
    void empty_result_is_returned_when_no_match_exists() {
        listingSearchPort.setListings(baseListings());

        SearchResult result = service.search(requestBuilder().keyword("nonexistent").build());

        assertTrue(result.items().isEmpty());
        assertEquals(0, result.page().totalElements());
    }

    @Test
    void no_filters_returns_all_listings() {
        listingSearchPort.setListings(baseListings());

        SearchResult result = service.search(requestBuilder().build());

        assertEquals(3, result.items().size());
    }

    @Test
    void boundary_conditions_are_inclusive() {
        listingSearchPort.setListings(baseListings());

        SearchResult result = service.search(requestBuilder().minimumPrice(100_000L).maximumPrice(100_000L).build());

        assertEquals(1, result.items().size());
        assertEquals(100_000L, result.items().get(0).dailyRateAmount());
    }

    @Test
    void availability_provider_is_called_for_each_candidate_when_needed() {
        listingSearchPort.setListings(baseListings());
        availabilityProvider.allowOnly(listingId("drill"));

        service.search(requestBuilder().requestedAvailabilityRange(range()).build());

        assertEquals(3, availabilityProvider.calls.size());
        assertTrue(availabilityProvider.calls.keySet().containsAll(List.of(listingId("drill"), listingId("saw"), listingId("mixer"))));
    }

    private List<SearchListingSummary> baseListings() {
        return List.of(
            summary("drill", "Hammer Drill", "Heavy duty hammer drill", "drills", "Drills", "drills", "Ho Chi Minh City", "District 1", 150_000L, 500_000L, true, createdAt(1)),
            summary("saw", "Circular Saw", "Portable saw", "saws", "Saws", "saws", "Ho Chi Minh City", "District 3", 100_000L, 400_000L, false, createdAt(2)),
            summary("mixer", "Paint Mixer", "Mixer", "mixers", "Mixers", "mixers", "Da Nang", "Hai Chau", 200_000L, 600_000L, true, createdAt(3))
        );
    }

    private SearchListingSummary summary(
            String suffix,
            String title,
            String description,
            String categoryKey,
            String categoryDisplayName,
            String categorySlug,
            String city,
            String district,
            long dailyRateAmount,
            long depositAmount,
            boolean deliveryAvailable,
            OffsetDateTime createdAt
    ) {
        return new SearchListingSummary(
                listingId(suffix),
                title,
                description,
                categoryId(categorySlug),
                categoryKey,
                categoryDisplayName,
                city,
                district,
                dailyRateAmount,
                depositAmount,
                "VND",
                deliveryAvailable,
                createdAt
        );
    }

    private OffsetDateTime createdAt(int daysAgo) {
        return OffsetDateTime.of(LocalDateTime.of(2026, 8, 15, 10, 0).minusDays(daysAgo), ZoneOffset.UTC);
    }

    private UUID listingId(String suffix) {
        return UUID.nameUUIDFromBytes(suffix.getBytes());
    }

    private UUID categoryId(String suffix) {
        return UUID.nameUUIDFromBytes(("category-" + suffix).getBytes());
    }

    private TimeRange range() {
        return new TimeRange(LocalDateTime.of(2026, 8, 20, 10, 0), LocalDateTime.of(2026, 8, 20, 12, 0));
    }

    private SearchRequestBuilder requestBuilder() {
        return new SearchRequestBuilder();
    }

    private final class SearchRequestBuilder {
        private String keyword;
        private UUID categoryId;
        private String city;
        private String district;
        private Long minimumPrice;
        private Long maximumPrice;
        private TimeRange requestedAvailabilityRange;
        private Boolean deliveryAvailable;
        private int pageNumber = 0;
        private int pageSize = 20;
        private SearchSort sort = SearchSort.NEWEST;

        SearchRequestBuilder keyword(String value) { this.keyword = value; return this; }
        SearchRequestBuilder categoryId(UUID value) { this.categoryId = value; return this; }
        SearchRequestBuilder city(String value) { this.city = value; return this; }
        SearchRequestBuilder district(String value) { this.district = value; return this; }
        SearchRequestBuilder minimumPrice(Long value) { this.minimumPrice = value; return this; }
        SearchRequestBuilder maximumPrice(Long value) { this.maximumPrice = value; return this; }
        SearchRequestBuilder requestedAvailabilityRange(TimeRange value) { this.requestedAvailabilityRange = value; return this; }
        SearchRequestBuilder deliveryAvailable(Boolean value) { this.deliveryAvailable = value; return this; }
        SearchRequestBuilder pageNumber(int value) { this.pageNumber = value; return this; }
        SearchRequestBuilder pageSize(int value) { this.pageSize = value; return this; }
        SearchRequestBuilder sort(SearchSort value) { this.sort = value; return this; }

        SearchRequest build() {
            return new SearchRequest(keyword, categoryId, city, district, minimumPrice, maximumPrice, requestedAvailabilityRange, deliveryAvailable, pageNumber, pageSize, sort);
        }
    }

    private static final class FakeListingSearchPort implements ListingSearchPort {
        private List<SearchListingSummary> listings = List.of();

        void setListings(List<SearchListingSummary> listings) {
            this.listings = List.copyOf(listings);
        }

        @Override
        public List<SearchListingSummary> findDiscoverableListings() {
            return listings;
        }
    }

    private static final class RecordingAvailabilityProvider implements AvailabilityProvider {
        private final Map<UUID, Boolean> availability = new LinkedHashMap<>();
        private final Map<UUID, TimeRange> calls = new LinkedHashMap<>();

        void allowOnly(UUID listingId) {
            availability.clear();
            availability.put(listingId, true);
        }

        @Override
        public boolean isAvailable(UUID listingId, TimeRange requestedRange) {
            calls.put(listingId, requestedRange);
            return availability.getOrDefault(listingId, false);
        }
    }
}