package com.toolshare.availability.application;

import com.toolshare.availability.domain.AvailabilityCalendar;

import java.util.Optional;
import java.util.UUID;

public interface AvailabilityCalendarSource {
    Optional<AvailabilityCalendar> findByListingId(UUID listingId);
}