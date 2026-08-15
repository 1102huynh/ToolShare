package com.toolshare.availability.domain;

import java.util.Objects;

public record MaintenanceWindow(TimeRange timeRange) {
    public MaintenanceWindow {
        Objects.requireNonNull(timeRange, "timeRange");
    }
}