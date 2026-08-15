package com.toolshare.search;

import com.toolshare.availability.application.AvailabilityFacade;
import com.toolshare.availability.domain.TimeRange;
import com.toolshare.search.infrastructure.AvailabilityProviderAdapter;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AvailabilityProviderAdapterTest {

    @Test
    void adapter_delegates_to_availability_facade() {
        UUID listingId = UUID.randomUUID();
        TimeRange requestedRange = new TimeRange(
                LocalDateTime.of(2026, 8, 20, 10, 0),
                LocalDateTime.of(2026, 8, 20, 12, 0)
        );

        AtomicInteger calls = new AtomicInteger(0);
        AvailabilityFacade facade = (id, range) -> {
            if (listingId.equals(id) && requestedRange.equals(range)) {
                calls.incrementAndGet();
            }
            return true;
        };

        AvailabilityProviderAdapter adapter = new AvailabilityProviderAdapter(facade);

        assertTrue(adapter.isAvailable(listingId, requestedRange));
        assertTrue(calls.get() == 1);
    }
}