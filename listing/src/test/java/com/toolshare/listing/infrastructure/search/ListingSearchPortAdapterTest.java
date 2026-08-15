package com.toolshare.listing.infrastructure.search;

import com.toolshare.identity.domain.IdentityAccount;
import com.toolshare.identity.infrastructure.persistence.IdentityAccountRepository;
import com.toolshare.listing.domain.Category;
import com.toolshare.listing.domain.ListingLocation;
import com.toolshare.listing.domain.ListingStatus;
import com.toolshare.listing.domain.ToolListing;
import com.toolshare.listing.infrastructure.persistence.CategoryRepository;
import com.toolshare.listing.infrastructure.persistence.ToolListingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureTestDatabase
class ListingSearchPortAdapterTest {

    @Autowired
    private IdentityAccountRepository identityAccountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ToolListingRepository toolListingRepository;

    @Autowired
    private ListingSearchPortAdapter adapter;

    @Test
    @Transactional
    void adapter_returns_only_active_listings_from_canonical_persistence() {
        IdentityAccount owner = identityAccountRepository.save(IdentityAccount.register("search-owner@example.com", "hashed-password"));
        owner.markEmailVerified();
        owner.activate();
        identityAccountRepository.save(owner);

        Category category = categoryRepository.save(new Category("drills", "Drills"));

        ToolListing activeListing = toolListingRepository.save(new ToolListing(
                owner.getId(),
                category,
                new ListingLocation("12 Nguyen Trai", null, "Ward 2", "District 5", "Ho Chi Minh City", "VN"),
                "Cordless Drill",
                "Reliable tool for home renovation",
                200000,
                800000,
                "VND",
                true,
                List.of()
        ));
            activeListing.submitForReview();
            activeListing.activate();
            toolListingRepository.save(activeListing);

        ToolListing pausedListing = toolListingRepository.save(new ToolListing(
                owner.getId(),
                category,
                new ListingLocation("45 Le Loi", null, "Ward 1", "District 1", "Ho Chi Minh City", "VN"),
                "Paused Drill",
                "Paused tool",
                100000,
                500000,
                "VND",
                false,
                List.of()
        ));
        pausedListing.submitForReview();
        pausedListing.activate();
        pausedListing.pause();
        toolListingRepository.save(pausedListing);

        List<com.toolshare.search.application.SearchListingSummary> summaries = adapter.findDiscoverableListings();

        assertEquals(1, summaries.size());
        assertEquals(activeListing.getId(), summaries.get(0).listingId());
        assertTrue(summaries.stream().allMatch(summary -> summary.deliveryAvailable()));
    }
}