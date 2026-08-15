package com.toolshare.listing.infrastructure.search;

import com.toolshare.listing.domain.ListingStatus;
import com.toolshare.listing.domain.ToolListing;
import com.toolshare.listing.infrastructure.persistence.ToolListingRepository;
import com.toolshare.search.application.ListingSearchPort;
import com.toolshare.search.application.SearchListingSummary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class ListingSearchPortAdapter implements ListingSearchPort {

    private final ToolListingRepository toolListingRepository;

    public ListingSearchPortAdapter(ToolListingRepository toolListingRepository) {
        this.toolListingRepository = toolListingRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SearchListingSummary> findDiscoverableListings() {
        return toolListingRepository.findAllByStatus(ListingStatus.ACTIVE).stream()
                .map(this::toSummary)
                .toList();
    }

    private SearchListingSummary toSummary(ToolListing listing) {
        return new SearchListingSummary(
                listing.getId(),
                listing.getTitle(),
                listing.getDescription(),
                listing.getCategory().getId(),
                listing.getCategory().getKey(),
                listing.getCategory().getDisplayName(),
                listing.getLocation().getCity(),
                listing.getLocation().getDistrict(),
                listing.getDailyRateAmount(),
                listing.getDepositAmount(),
                listing.getCurrency(),
                listing.isDeliveryAvailable(),
                listing.getCreatedAt()
        );
    }
}