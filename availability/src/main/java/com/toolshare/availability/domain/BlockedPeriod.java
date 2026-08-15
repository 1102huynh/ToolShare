package com.toolshare.availability.domain;

import java.util.Objects;

public record BlockedPeriod(TimeRange timeRange) {
    public BlockedPeriod {
        Objects.requireNonNull(timeRange, "timeRange");
    }
}