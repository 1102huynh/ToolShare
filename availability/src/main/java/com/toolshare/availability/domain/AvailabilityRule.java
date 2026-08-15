package com.toolshare.availability.domain;

import java.util.Objects;

public record AvailabilityRule(TimeRange timeRange, RecurrenceRule recurrenceRule) {
    public AvailabilityRule {
        Objects.requireNonNull(timeRange, "timeRange");
    }

    public AvailabilityRule(TimeRange timeRange) {
        this(timeRange, null);
    }
}