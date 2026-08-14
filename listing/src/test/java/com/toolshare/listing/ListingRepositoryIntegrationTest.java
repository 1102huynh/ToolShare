package com.toolshare.listing;

import com.toolshare.identity.domain.IdentityAccount;
import com.toolshare.identity.infrastructure.persistence.IdentityAccountRepository;
import com.toolshare.listing.domain.Category;
import com.toolshare.listing.domain.ListingLocation;
import com.toolshare.listing.domain.ListingStatus;
import com.toolshare.listing.domain.ToolImage;
import com.toolshare.listing.domain.ToolListing;
import com.toolshare.listing.infrastructure.persistence.CategoryRepository;
import com.toolshare.listing.infrastructure.persistence.ToolListingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureTestDatabase
class ListingRepositoryIntegrationTest {

    @Autowired
    private IdentityAccountRepository identityAccountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ToolListingRepository toolListingRepository;

    @Test
    @Transactional
    void listing_persists_owner_category_location_images_and_status() {
        IdentityAccount owner = identityAccountRepository.save(IdentityAccount.register("owner-listing@example.com", "hashed-password"));
        owner.markEmailVerified();
        owner.activate();
        identityAccountRepository.save(owner);

        Category category = categoryRepository.save(new Category("ladders", "Ladders"));

        ToolListing listing = new ToolListing(
                owner.getId(),
                category,
                new ListingLocation("456 Nguyen Hue", "Apartment 2", "Ward Ben Nghe", "District 1", "Ho Chi Minh City", "VN"),
                "Aluminium Ladder",
                "Three-section folding ladder",
                175000,
                500000,
                "VND",
                false,
                List.of(new ToolImage("listing-images/ladder-front.jpg", 0), new ToolImage("listing-images/ladder-side.jpg", 1))
        );
        listing.submitForReview();

        ToolListing saved = toolListingRepository.save(listing);

        ToolListing reloaded = toolListingRepository.findById(saved.getId()).orElseThrow();
        assertEquals(owner.getId(), reloaded.getOwnerAccountId());
        assertEquals(category.getId(), reloaded.getCategory().getId());
        assertEquals(ListingStatus.PENDING_REVIEW, reloaded.getStatus());
        assertEquals("Ho Chi Minh City", reloaded.getLocation().getCity());
        assertEquals(2, reloaded.getImages().size());
    }

    @Test
    @Transactional
    void category_key_lookup_and_owner_lookup_work() {
        IdentityAccount owner = identityAccountRepository.save(IdentityAccount.register("owner-lookup@example.com", "hashed-password"));
        owner.markEmailVerified();
        owner.activate();
        identityAccountRepository.save(owner);

        Category category = categoryRepository.save(new Category("generators", "Generators"));
        ToolListing listing = toolListingRepository.save(new ToolListing(
                owner.getId(),
                category,
                new ListingLocation("789 Le Loi", null, "Ward 7", "District 3", "Ho Chi Minh City", "VN"),
                "Portable Generator",
                "2kW generator",
                300000,
                1200000,
                "VND",
                true,
                List.of()
        ));

        assertTrue(categoryRepository.existsByKey("generators"));
        assertTrue(toolListingRepository.findByIdAndOwnerAccountId(listing.getId(), owner.getId()).isPresent());
    }
}