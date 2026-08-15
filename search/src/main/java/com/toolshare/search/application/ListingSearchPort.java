package com.toolshare.search.application;

import java.util.List;

public interface ListingSearchPort {
    List<SearchListingSummary> findDiscoverableListings();
}