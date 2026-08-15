package com.toolshare.availability.application;

import com.toolshare.availability.domain.AvailabilityCalendar;
import com.toolshare.availability.domain.TimeRange;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class AvailabilityFacadeService implements AvailabilityFacade {

    private final AvailabilityCalendarSource calendarSource;
    private final AvailabilityCalculationService availabilityCalculationService;
    private final BookingConflictProvider bookingConflictProvider;

    public AvailabilityFacadeService(
            AvailabilityCalendarSource calendarSource,
            AvailabilityCalculationService availabilityCalculationService,
            BookingConflictProvider bookingConflictProvider
    ) {
        this.calendarSource = Objects.requireNonNull(calendarSource, "calendarSource");
        this.availabilityCalculationService = Objects.requireNonNull(availabilityCalculationService, "availabilityCalculationService");
        this.bookingConflictProvider = Objects.requireNonNull(bookingConflictProvider, "bookingConflictProvider");
    }

    @Override
    public boolean isAvailable(UUID listingId, TimeRange requestedRange) {
        Objects.requireNonNull(listingId, "listingId");
        Objects.requireNonNull(requestedRange, "requestedRange");

        Optional<AvailabilityCalendar> calendar = calendarSource.findByListingId(listingId);
        if (calendar.isEmpty()) {
            return false;
        }

        return availabilityCalculationService.calculate(calendar.get(), requestedRange, bookingConflictProvider).available();
    }
}