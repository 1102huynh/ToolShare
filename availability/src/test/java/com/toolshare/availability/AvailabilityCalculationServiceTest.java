package com.toolshare.availability;

import com.toolshare.availability.application.AvailabilityCalculationService;
import com.toolshare.availability.application.BookingConflictProvider;
import com.toolshare.availability.domain.AvailabilityCalendar;
import com.toolshare.availability.domain.AvailabilityResult;
import com.toolshare.availability.domain.AvailabilityRule;
import com.toolshare.availability.domain.BlockedPeriod;
import com.toolshare.availability.domain.MaintenanceWindow;
import com.toolshare.availability.domain.TimeRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvailabilityCalculationServiceTest {

    private AvailabilityCalculationService service;

    @BeforeEach
    void setUp() {
        service = new AvailabilityCalculationService();
    }

    @Test
    void fully_available_range_is_available() {
        AvailabilityResult result = service.calculate(calendar(
                rules(range(9, 17))
        ), range(10, 11), noConflicts());

        assertTrue(result.available());
    }

    @Test
    void blocked_period_makes_range_unavailable() {
        AvailabilityResult result = service.calculate(calendar(
                rules(range(9, 17)),
                blocked(range(10, 12))
        ), range(10, 11), noConflicts());

        assertFalse(result.available());
    }

    @Test
    void blocked_period_overlapping_only_part_of_range_makes_range_unavailable() {
        AvailabilityResult result = service.calculate(calendar(
                rules(range(9, 17)),
                blocked(range(10, 12))
        ), range(9, 13), noConflicts());

        assertFalse(result.available());
    }

    @Test
    void maintenance_window_makes_range_unavailable() {
        AvailabilityResult result = service.calculate(calendar(
                rules(range(9, 17)),
                maintenance(range(13, 14))
        ), range(13, 14), noConflicts());

        assertFalse(result.available());
    }

    @Test
    void confirmed_booking_makes_range_unavailable() {
        AvailabilityResult result = service.calculate(calendar(
                rules(range(9, 17))
        ), range(11, 12), bookingConflicts(range(11, 12)));

        assertFalse(result.available());
    }

    @Test
    void running_booking_makes_range_unavailable() {
        AvailabilityResult result = service.calculate(calendar(
                rules(range(9, 17))
        ), range(12, 13), bookingConflicts(range(12, 13)));

        assertFalse(result.available());
    }

    @Test
    void multiple_exclusion_intervals_make_range_unavailable() {
        AvailabilityResult result = service.calculate(calendar(
                rules(range(9, 17)),
                blocked(range(10, 11)),
                maintenance(range(12, 13))
        ), range(9, 17), bookingConflicts(range(14, 15)));

        assertFalse(result.available());
    }

    @Test
    void adjacent_intervals_are_merged_for_continuous_availability() {
        AvailabilityResult result = service.calculate(calendar(
                rules(range(9, 12), range(12, 17))
        ), range(9, 17), noConflicts());

        assertTrue(result.available());
    }

    @Test
    void overlapping_exclusion_intervals_are_handled_consistently() {
        AvailabilityResult result = service.calculate(calendar(
                rules(range(9, 17)),
                blocked(range(10, 13)),
                maintenance(range(11, 14))
        ), range(9, 17), noConflicts());

        assertFalse(result.available());
    }

    @Test
    void empty_availability_is_unavailable() {
        AvailabilityResult result = service.calculate(calendar(), range(10, 11), noConflicts());

        assertFalse(result.available());
    }

    @Test
    void no_booking_conflicts_is_available() {
        AvailabilityResult result = service.calculate(calendar(
                rules(range(9, 17))
        ), range(10, 11), noConflicts());

        assertTrue(result.available());
    }

    @Test
    void baseline_rules_can_span_adjacent_windows_without_a_gap() {
        AvailabilityResult result = service.calculate(calendar(
                rules(range(9, 12), range(12, 17))
        ), range(10, 16), noConflicts());

        assertTrue(result.available());
    }

    private BookingConflictProvider noConflicts() {
        return (listingId, requestedRange) -> List.of();
    }

    private BookingConflictProvider bookingConflicts(TimeRange... conflicts) {
        List<TimeRange> ranges = List.of(conflicts);
        return (listingId, requestedRange) -> ranges;
    }

    private AvailabilityCalendar calendar(Object... components) {
        List<AvailabilityRule> rules = new java.util.ArrayList<>();
        List<BlockedPeriod> blockedPeriods = new java.util.ArrayList<>();
        List<MaintenanceWindow> maintenanceWindows = new java.util.ArrayList<>();

        for (Object component : components) {
            if (component instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof AvailabilityRule rule) {
                        rules.add(rule);
                    } else if (item instanceof BlockedPeriod blockedPeriod) {
                        blockedPeriods.add(blockedPeriod);
                    } else if (item instanceof MaintenanceWindow maintenanceWindow) {
                        maintenanceWindows.add(maintenanceWindow);
                    }
                }
            }
        }

        return new AvailabilityCalendar(UUID.randomUUID(), rules, blockedPeriods, maintenanceWindows);
    }

    private List<AvailabilityRule> rules(TimeRange... ranges) {
        return java.util.Arrays.stream(ranges).map(AvailabilityRule::new).toList();
    }

    private List<BlockedPeriod> blocked(TimeRange... ranges) {
        return java.util.Arrays.stream(ranges).map(BlockedPeriod::new).toList();
    }

    private List<MaintenanceWindow> maintenance(TimeRange... ranges) {
        return java.util.Arrays.stream(ranges).map(MaintenanceWindow::new).toList();
    }

    private TimeRange range(int startHour, int endHour) {
        return new TimeRange(
                LocalDateTime.of(2026, 8, 15, startHour, 0),
                LocalDateTime.of(2026, 8, 15, endHour, 0)
        );
    }

    private TimeRange range(int startHour) {
        return new TimeRange(
                LocalDateTime.of(2026, 8, 15, startHour, 0),
                LocalDateTime.of(2026, 8, 15, startHour + 1, 0)
        );
    }
}