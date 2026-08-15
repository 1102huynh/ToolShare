package com.toolshare.availability.domain;

import com.toolshare.common.exception.ValidationException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

public record TimeRange(LocalDateTime start, LocalDateTime end) {

    public TimeRange {
        start = Objects.requireNonNull(start, "start");
        end = Objects.requireNonNull(end, "end");
        if (!start.isBefore(end)) {
            throw new ValidationException("start must be before end");
        }
    }

    public boolean overlaps(TimeRange other) {
        Objects.requireNonNull(other, "other");
        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    public boolean isAdjacentTo(TimeRange other) {
        Objects.requireNonNull(other, "other");
        return end.equals(other.start) || start.equals(other.end);
    }

    public boolean contains(TimeRange other) {
        Objects.requireNonNull(other, "other");
        return !start.isAfter(other.start) && !end.isBefore(other.end);
    }

    public Optional<TimeRange> intersection(TimeRange other) {
        Objects.requireNonNull(other, "other");
        if (!overlaps(other)) {
            return Optional.empty();
        }

        LocalDateTime intersectionStart = start.isAfter(other.start) ? start : other.start;
        LocalDateTime intersectionEnd = end.isBefore(other.end) ? end : other.end;
        return Optional.of(new TimeRange(intersectionStart, intersectionEnd));
    }
}