package com.toolshare.search.application;

import com.toolshare.availability.domain.TimeRange;
import com.toolshare.common.exception.ValidationException;

import java.util.Locale;
import java.util.UUID;

public record SearchRequest(
        String keyword,
        UUID categoryId,
        String city,
        String district,
        Long minimumPrice,
        Long maximumPrice,
        TimeRange requestedAvailabilityRange,
        Boolean deliveryAvailable,
        int pageNumber,
        int pageSize,
        SearchSort sort
) {
    public SearchRequest {
        keyword = normalizeText(keyword);
        city = normalizeText(city);
        district = normalizeText(district);
        if (minimumPrice != null && minimumPrice < 0) {
            throw new ValidationException("minimumPrice must be zero or greater");
        }
        if (maximumPrice != null && maximumPrice < 0) {
            throw new ValidationException("maximumPrice must be zero or greater");
        }
        if (minimumPrice != null && maximumPrice != null && minimumPrice > maximumPrice) {
            throw new ValidationException("minimumPrice must be less than or equal to maximumPrice");
        }
        if (pageNumber < 0) {
            throw new ValidationException("pageNumber must be zero or greater");
        }
        if (pageSize <= 0) {
            throw new ValidationException("pageSize must be greater than zero");
        }
        sort = sort == null ? SearchSort.NEWEST : sort;
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }
}