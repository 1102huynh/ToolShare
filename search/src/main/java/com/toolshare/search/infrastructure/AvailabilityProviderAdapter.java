package com.toolshare.search.infrastructure;

import com.toolshare.availability.application.AvailabilityFacade;
import com.toolshare.availability.domain.TimeRange;
import com.toolshare.search.application.AvailabilityProvider;

import java.util.Objects;
import java.util.UUID;

public final class AvailabilityProviderAdapter implements AvailabilityProvider {

    private final AvailabilityFacade availabilityFacade;

    public AvailabilityProviderAdapter(AvailabilityFacade availabilityFacade) {
        this.availabilityFacade = Objects.requireNonNull(availabilityFacade, "availabilityFacade");
    }

    @Override
    public boolean isAvailable(UUID listingId, TimeRange requestedRange) {
        return availabilityFacade.isAvailable(listingId, requestedRange);
    }
}