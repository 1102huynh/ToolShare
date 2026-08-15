package com.toolshare.search.application;

import com.toolshare.availability.domain.TimeRange;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class SearchService {

    private final ListingSearchPort listingSearchPort;
    private final AvailabilityProvider availabilityProvider;

    public SearchService(ListingSearchPort listingSearchPort, AvailabilityProvider availabilityProvider) {
        this.listingSearchPort = Objects.requireNonNull(listingSearchPort, "listingSearchPort");
        this.availabilityProvider = Objects.requireNonNull(availabilityProvider, "availabilityProvider");
    }

    public SearchResult search(SearchRequest request) {
        Objects.requireNonNull(request, "request");

        List<SearchListingSummary> filtered = new ArrayList<>();
        for (SearchListingSummary listing : listingSearchPort.findDiscoverableListings()) {
            if (!matchesKeyword(listing, request.keyword())) {
                continue;
            }
            if (!matchesCategory(listing, request.categoryId())) {
                continue;
            }
            if (!matchesCity(listing, request.city())) {
                continue;
            }
            if (!matchesDistrict(listing, request.district())) {
                continue;
            }
            if (!matchesMinimumPrice(listing, request.minimumPrice())) {
                continue;
            }
            if (!matchesMaximumPrice(listing, request.maximumPrice())) {
                continue;
            }
            if (!matchesDelivery(listing, request.deliveryAvailable())) {
                continue;
            }
            if (!matchesAvailability(listing, request.requestedAvailabilityRange())) {
                continue;
            }
            filtered.add(listing);
        }

        filtered.sort(comparatorFor(request.sort()));

        int totalElements = filtered.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / request.pageSize());
        int fromIndex = request.pageNumber() * request.pageSize();
        List<SearchListingSummary> pageItems;
        if (fromIndex >= totalElements) {
            pageItems = List.of();
        } else {
            int toIndex = Math.min(fromIndex + request.pageSize(), totalElements);
            pageItems = filtered.subList(fromIndex, toIndex);
        }

        SearchPageMetadata metadata = new SearchPageMetadata(
                request.pageNumber(),
                request.pageSize(),
                totalElements,
                totalPages,
                request.pageNumber() + 1 < totalPages,
                request.pageNumber() > 0 && totalElements > 0
        );
        return new SearchResult(pageItems, metadata);
    }

    private boolean matchesKeyword(SearchListingSummary listing, String keyword) {
        if (keyword == null) {
            return true;
        }

        String normalized = keyword.toLowerCase(Locale.ROOT);
        return contains(listing.title(), normalized)
                || contains(listing.description(), normalized)
                || contains(listing.categoryKey(), normalized)
                || contains(listing.categoryDisplayName(), normalized);
    }

    private boolean matchesCategory(SearchListingSummary listing, UUID categoryId) {
        return categoryId == null || categoryId.equals(listing.categoryId());
    }

    private boolean matchesCity(SearchListingSummary listing, String city) {
        return city == null || equalsIgnoreCase(listing.city(), city);
    }

    private boolean matchesDistrict(SearchListingSummary listing, String district) {
        return district == null || equalsIgnoreCase(listing.district(), district);
    }

    private boolean matchesMinimumPrice(SearchListingSummary listing, Long minimumPrice) {
        return minimumPrice == null || listing.dailyRateAmount() >= minimumPrice;
    }

    private boolean matchesMaximumPrice(SearchListingSummary listing, Long maximumPrice) {
        return maximumPrice == null || listing.dailyRateAmount() <= maximumPrice;
    }

    private boolean matchesDelivery(SearchListingSummary listing, Boolean deliveryAvailable) {
        return deliveryAvailable == null || listing.deliveryAvailable() == deliveryAvailable;
    }

    private boolean matchesAvailability(SearchListingSummary listing, TimeRange requestedAvailabilityRange) {
        return requestedAvailabilityRange == null || availabilityProvider.isAvailable(listing.listingId(), requestedAvailabilityRange);
    }

    private Comparator<SearchListingSummary> comparatorFor(SearchSort sort) {
        return switch (sort) {
            case NEWEST -> Comparator.comparing(SearchListingSummary::createdAt).reversed()
                    .thenComparing(SearchListingSummary::listingId);
            case PRICE_ASC -> Comparator.comparingLong(SearchListingSummary::dailyRateAmount)
                .thenComparing(SearchListingSummary::createdAt, Comparator.reverseOrder())
                .thenComparing(SearchListingSummary::listingId);
                case PRICE_DESC -> Comparator.comparingLong(SearchListingSummary::dailyRateAmount).reversed()
                .thenComparing(SearchListingSummary::createdAt, Comparator.reverseOrder())
                .thenComparing(SearchListingSummary::listingId);
        };
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }
}