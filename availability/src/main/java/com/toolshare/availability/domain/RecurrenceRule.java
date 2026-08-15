package com.toolshare.availability.domain;

import com.toolshare.common.exception.ValidationException;

public record RecurrenceRule(String value) {
    public RecurrenceRule {
        if (value == null || value.trim().isBlank()) {
            throw new ValidationException("recurrenceRule is required");
        }
        value = value.trim();
    }
}