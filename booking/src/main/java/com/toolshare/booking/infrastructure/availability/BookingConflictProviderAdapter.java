package com.toolshare.booking.infrastructure.availability;

import com.toolshare.availability.application.BookingConflictProvider;
import com.toolshare.availability.domain.TimeRange;
import com.toolshare.booking.domain.Booking;
import com.toolshare.booking.domain.BookingState;
import com.toolshare.booking.infrastructure.persistence.BookingRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class BookingConflictProviderAdapter implements BookingConflictProvider {

    private final BookingRepository bookingRepository;

    public BookingConflictProviderAdapter(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Override
    public List<TimeRange> findConflictingBookingRanges(UUID listingId, TimeRange requestedRange) {
        Objects.requireNonNull(listingId, "listingId");
        Objects.requireNonNull(requestedRange, "requestedRange");

        List<Booking> overlapping = bookingRepository.findOverlappingByListingAndStates(
                listingId,
                requestedRange.start(),
                requestedRange.end(),
                List.copyOf(BookingState.overlapBlockingStates())
        );

        return overlapping.stream()
                .map(booking -> new TimeRange(booking.getRequestedStartAt(), booking.getRequestedEndAt()))
                .toList();
    }
}
