package com.toolshare.availability;

import com.toolshare.availability.domain.TimeRange;
import com.toolshare.common.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimeRangeTest {

    @Test
    void valid_time_range_is_created() {
        TimeRange range = assertDoesNotThrow(() -> new TimeRange(
                LocalDateTime.of(2026, 8, 15, 9, 0),
                LocalDateTime.of(2026, 8, 15, 10, 0)
        ));

        assertEquals(LocalDateTime.of(2026, 8, 15, 9, 0), range.start());
        assertEquals(LocalDateTime.of(2026, 8, 15, 10, 0), range.end());
    }

    @Test
    void zero_length_range_is_rejected() {
        assertThrows(ValidationException.class, () -> new TimeRange(
                LocalDateTime.of(2026, 8, 15, 9, 0),
                LocalDateTime.of(2026, 8, 15, 9, 0)
        ));
    }

    @Test
    void reversed_range_is_rejected() {
        assertThrows(ValidationException.class, () -> new TimeRange(
                LocalDateTime.of(2026, 8, 15, 10, 0),
                LocalDateTime.of(2026, 8, 15, 9, 0)
        ));
    }
}