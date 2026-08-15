package com.toolshare.availability.infrastructure;

import com.toolshare.availability.application.AvailabilityCalendarSource;
import com.toolshare.availability.domain.AvailabilityCalendar;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryAvailabilityCalendarSource implements AvailabilityCalendarSource {

    private final Map<UUID, AvailabilityCalendar> calendars;

    public InMemoryAvailabilityCalendarSource(Map<UUID, AvailabilityCalendar> calendars) {
        this.calendars = new HashMap<>(calendars == null ? Map.of() : calendars);
    }

    @Override
    public Optional<AvailabilityCalendar> findByListingId(UUID listingId) {
        return Optional.ofNullable(calendars.get(listingId));
    }
}