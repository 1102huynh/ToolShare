package com.toolshare.availability.application;

import com.toolshare.availability.domain.AvailabilityCalendar;
import com.toolshare.availability.domain.AvailabilityResult;
import com.toolshare.availability.domain.BlockedPeriod;
import com.toolshare.availability.domain.MaintenanceWindow;
import com.toolshare.availability.domain.TimeRange;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class AvailabilityCalculationService {

    public AvailabilityResult calculate(
            AvailabilityCalendar calendar,
            TimeRange requestedRange,
            BookingConflictProvider bookingConflictProvider
    ) {
        Objects.requireNonNull(calendar, "calendar");
        Objects.requireNonNull(requestedRange, "requestedRange");
        Objects.requireNonNull(bookingConflictProvider, "bookingConflictProvider");

        List<TimeRange> baselineAvailability = mergeIntervals(
                calendar.getAvailabilityRules().stream()
                        .map(rule -> rule.timeRange())
                        .toList()
        );
        if (baselineAvailability.isEmpty()) {
            return AvailabilityResult.unavailable("No availability rules are defined", List.of());
        }

        List<TimeRange> exclusionRanges = new ArrayList<>();
        addIntersections(exclusionRanges, requestedRange, calendar.getBlockedPeriods().stream().map(BlockedPeriod::timeRange).toList());
        addIntersections(exclusionRanges, requestedRange, calendar.getMaintenanceWindows().stream().map(MaintenanceWindow::timeRange).toList());
        List<TimeRange> bookingConflicts = bookingConflictProvider.findConflictingBookingRanges(calendar.getListingId(), requestedRange);
        addIntersections(exclusionRanges, requestedRange, bookingConflicts == null ? List.of() : bookingConflicts);

        List<TimeRange> effectiveAvailability = subtractAll(baselineAvailability, mergeIntervals(exclusionRanges));
        for (TimeRange interval : effectiveAvailability) {
            if (interval.contains(requestedRange)) {
                return AvailabilityResult.availableResult();
            }
        }

        return AvailabilityResult.unavailable(
                "Requested range is unavailable",
                mergeIntervals(exclusionRanges)
        );
    }

    private void addIntersections(List<TimeRange> target, TimeRange requestedRange, List<TimeRange> sourceRanges) {
        for (TimeRange range : sourceRanges) {
            if (range == null) {
                continue;
            }
            range.intersection(requestedRange).ifPresent(target::add);
        }
    }

    private List<TimeRange> subtractAll(List<TimeRange> source, List<TimeRange> exclusions) {
        List<TimeRange> current = new ArrayList<>(source);
        for (TimeRange exclusion : exclusions) {
            current = subtract(current, exclusion);
        }
        return mergeIntervals(current);
    }

    private List<TimeRange> subtract(List<TimeRange> source, TimeRange exclusion) {
        List<TimeRange> result = new ArrayList<>();
        for (TimeRange interval : source) {
            if (!interval.overlaps(exclusion)) {
                result.add(interval);
                continue;
            }

            if (interval.start().isBefore(exclusion.start())) {
                result.add(new TimeRange(interval.start(), min(interval.end(), exclusion.start())));
            }

            if (interval.end().isAfter(exclusion.end())) {
                result.add(new TimeRange(max(interval.start(), exclusion.end()), interval.end()));
            }
        }
        return result;
    }

    private List<TimeRange> mergeIntervals(List<TimeRange> intervals) {
        List<TimeRange> sorted = intervals.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(TimeRange::start).thenComparing(TimeRange::end))
                .toList();

        if (sorted.isEmpty()) {
            return List.of();
        }

        List<TimeRange> merged = new ArrayList<>();
        TimeRange current = sorted.get(0);
        for (int index = 1; index < sorted.size(); index++) {
            TimeRange next = sorted.get(index);
            if (next.start().isAfter(current.end())) {
                merged.add(current);
                current = next;
            } else {
                current = new TimeRange(current.start(), max(current.end(), next.end()));
            }
        }
        merged.add(current);
        return merged;
    }

    private static java.time.LocalDateTime min(java.time.LocalDateTime left, java.time.LocalDateTime right) {
        return left.isBefore(right) ? left : right;
    }

    private static java.time.LocalDateTime max(java.time.LocalDateTime left, java.time.LocalDateTime right) {
        return left.isAfter(right) ? left : right;
    }
}