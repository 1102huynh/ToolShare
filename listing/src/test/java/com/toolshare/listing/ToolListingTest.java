package com.toolshare.listing;

import com.toolshare.listing.domain.Category;
import com.toolshare.listing.domain.ListingLocation;
import com.toolshare.listing.domain.ListingStatus;
import com.toolshare.listing.domain.ToolImage;
import com.toolshare.listing.domain.ToolListing;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolListingTest {

    @Test
    void new_listing_starts_as_draft() {
        ToolListing listing = createListing();

        assertEquals(ListingStatus.DRAFT, listing.getStatus());
    }

    @Test
    void draft_to_pending_review_succeeds() {
        ToolListing listing = createListing();

        listing.submitForReview();

        assertEquals(ListingStatus.PENDING_REVIEW, listing.getStatus());
    }

    @Test
    void pending_review_to_active_succeeds() {
        ToolListing listing = createListing();
        listing.submitForReview();

        listing.activate();

        assertEquals(ListingStatus.ACTIVE, listing.getStatus());
    }

    @Test
    void active_to_paused_succeeds() {
        ToolListing listing = activateListing();

        listing.pause();

        assertEquals(ListingStatus.PAUSED, listing.getStatus());
    }

    @Test
    void active_to_suspended_succeeds() {
        ToolListing listing = activateListing();

        listing.suspend();

        assertEquals(ListingStatus.SUSPENDED, listing.getStatus());
    }

    @Test
    void active_to_archived_succeeds() {
        ToolListing listing = activateListing();

        listing.archive();

        assertEquals(ListingStatus.ARCHIVED, listing.getStatus());
    }

    @Test
    void invalid_transitions_fail() {
        ToolListing draftListing = createListing();
        ToolListing pendingReviewListing = createListing();
        pendingReviewListing.submitForReview();
        ToolListing activeListing = activateListing();

        assertThrows(IllegalStateException.class, draftListing::activate);
        assertThrows(IllegalStateException.class, draftListing::pause);
        assertThrows(IllegalStateException.class, draftListing::suspend);
        assertThrows(IllegalStateException.class, draftListing::archive);
        assertThrows(IllegalStateException.class, pendingReviewListing::pause);
        assertThrows(IllegalStateException.class, pendingReviewListing::suspend);
        assertThrows(IllegalStateException.class, pendingReviewListing::archive);
        activeListing.pause();
        assertThrows(IllegalStateException.class, activeListing::activate);
    }

    private ToolListing activateListing() {
        ToolListing listing = createListing();
        listing.submitForReview();
        listing.activate();
        return listing;
    }

    private ToolListing createListing() {
        return new ToolListing(
                UUID.randomUUID(),
                new Category("power-drills", "Power Drills"),
                new ListingLocation("123 Tran Hung Dao", null, "Ward 1", "District 1", "Ho Chi Minh City", "VN"),
                "Bosch Hammer Drill",
                "Heavy duty rotary hammer drill",
                250000,
                1000000,
                "VND",
                true,
                List.of(new ToolImage("listing-images/drill-front.jpg", 0))
        );
    }
}