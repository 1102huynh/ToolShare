package com.toolshare.availability.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class AvailabilityCalendar {

    private final UUID listingId;
    private final List<AvailabilityRule> availabilityRules;
    private final List<BlockedPeriod> blockedPeriods;
    private final List<MaintenanceWindow> maintenanceWindows;

    public AvailabilityCalendar(
            UUID listingId,
            List<AvailabilityRule> availabilityRules,
            List<BlockedPeriod> blockedPeriods,
            List<MaintenanceWindow> maintenanceWindows
    ) {
        this.listingId = Objects.requireNonNull(listingId, "listingId");
        this.availabilityRules = immutableList(availabilityRules);
        this.blockedPeriods = immutableList(blockedPeriods);
        this.maintenanceWindows = immutableList(maintenanceWindows);
    }

    public UUID getListingId() {
        return listingId;
    }

    public List<AvailabilityRule> getAvailabilityRules() {
        return availabilityRules;
    }

    public List<BlockedPeriod> getBlockedPeriods() {
        return blockedPeriods;
    }

    public List<MaintenanceWindow> getMaintenanceWindows() {
        return maintenanceWindows;
    }

    private static <T> List<T> immutableList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}