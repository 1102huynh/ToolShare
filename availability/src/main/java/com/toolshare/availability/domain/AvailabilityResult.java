package com.toolshare.availability.domain;

import java.util.List;

public record AvailabilityResult(boolean available, String reason, List<TimeRange> conflictingRanges) {
    public AvailabilityResult {
        conflictingRanges = conflictingRanges == null ? List.of() : List.copyOf(conflictingRanges);
    }

    public static AvailabilityResult availableResult() {
        return new AvailabilityResult(true, null, List.of());
    }

    public static AvailabilityResult unavailable(String reason, List<TimeRange> conflictingRanges) {
        return new AvailabilityResult(false, reason, conflictingRanges);
    }
}