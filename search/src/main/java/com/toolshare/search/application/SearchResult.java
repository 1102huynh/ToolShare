package com.toolshare.search.application;

import java.util.List;

public record SearchResult(List<SearchListingSummary> items, SearchPageMetadata page) {
    public SearchResult {
        items = items == null ? List.of() : List.copyOf(items);
    }
}