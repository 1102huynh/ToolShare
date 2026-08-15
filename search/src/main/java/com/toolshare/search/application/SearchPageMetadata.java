package com.toolshare.search.application;

public record SearchPageMetadata(
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
}