package com.toolshare.booking;

import com.toolshare.availability.application.AvailabilityCalculationService;
import com.toolshare.availability.domain.AvailabilityCalendar;
import com.toolshare.availability.domain.AvailabilityResult;
import com.toolshare.availability.domain.AvailabilityRule;
import com.toolshare.availability.domain.TimeRange;
import com.toolshare.booking.domain.Booking;
import com.toolshare.booking.domain.BookingPolicy;
import com.toolshare.booking.infrastructure.availability.BookingConflictProviderAdapter;
import com.toolshare.booking.infrastructure.persistence.BookingRepository;
import com.toolshare.identity.domain.IdentityAccount;
import com.toolshare.identity.infrastructure.persistence.IdentityAccountRepository;
import com.toolshare.listing.domain.Category;
import com.toolshare.listing.domain.ListingLocation;
import com.toolshare.listing.domain.ToolListing;
import com.toolshare.listing.infrastructure.persistence.CategoryRepository;
import com.toolshare.listing.infrastructure.persistence.ToolListingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(classes = BookingApplication.class)
@AutoConfigureTestDatabase
class BookingConflictProviderAdapterIntegrationTest {

    @Autowired
    private BookingRepository bookingRepository;

        @Autowired
        private IdentityAccountRepository identityAccountRepository;

        @Autowired
        private CategoryRepository categoryRepository;

        @Autowired
        private ToolListingRepository toolListingRepository;

    @Autowired
    private BookingConflictProviderAdapter bookingConflictProviderAdapter;

    @Test
    @Transactional
    void provider_returns_only_active_overlapping_booking_ranges() {
        Fixture fixture = createFixture();
        UUID listingId = fixture.listing.getId();
        LocalDateTime start = LocalDateTime.now().plusDays(3).withMinute(0).withSecond(0).withNano(0);

        Booking active = bookingRepository.save(Booking.create(
                listingId,
                fixture.renter.getId(),
                fixture.owner.getId(),
                start,
                start.plusHours(2),
                BookingPolicy.OWNER_APPROVAL
        ));

        Booking cancelled = bookingRepository.save(Booking.create(
                listingId,
                fixture.secondRenter.getId(),
                fixture.owner.getId(),
                start.plusHours(3),
                start.plusHours(5),
                BookingPolicy.OWNER_APPROVAL
        ));
        cancelled.cancel();
        bookingRepository.save(cancelled);

        List<TimeRange> conflicts = bookingConflictProviderAdapter.findConflictingBookingRanges(
                listingId,
                new TimeRange(start.plusMinutes(30), start.plusHours(1).plusMinutes(30))
        );

        assertEquals(1, conflicts.size());
        assertEquals(active.getRequestedStartAt(), conflicts.get(0).start());
        assertEquals(active.getRequestedEndAt(), conflicts.get(0).end());
    }

    @Test
    @Transactional
    void availability_calculation_uses_booking_conflicts_and_marks_range_unavailable() {
        Fixture fixture = createFixture();
        UUID listingId = fixture.listing.getId();
        LocalDateTime day = LocalDateTime.now().plusDays(5).withMinute(0).withSecond(0).withNano(0);

        Booking active = bookingRepository.save(Booking.create(
                listingId,
                fixture.renter.getId(),
                fixture.owner.getId(),
                day.plusHours(10),
                day.plusHours(12),
                BookingPolicy.OWNER_APPROVAL
        ));
        active.approve();
        bookingRepository.save(active);

        AvailabilityCalculationService calculationService = new AvailabilityCalculationService();
        AvailabilityCalendar calendar = new AvailabilityCalendar(
                listingId,
                List.of(new AvailabilityRule(new TimeRange(day.plusHours(9), day.plusHours(17)))),
                List.of(),
                List.of()
        );

        AvailabilityResult result = calculationService.calculate(
                calendar,
                new TimeRange(day.plusHours(10), day.plusHours(11)),
                bookingConflictProviderAdapter
        );

        assertFalse(result.available());
    }

        private Fixture createFixture() {
                IdentityAccount owner = createActiveAccount("owner-booking-conflict-" + UUID.randomUUID() + "@example.com");
                IdentityAccount renter = createActiveAccount("renter-booking-conflict-" + UUID.randomUUID() + "@example.com");
                IdentityAccount secondRenter = createActiveAccount("renter2-booking-conflict-" + UUID.randomUUID() + "@example.com");

                Category category = categoryRepository.save(new Category("booking-conflicts-" + UUID.randomUUID(), "Booking Conflicts"));
                ToolListing listing = new ToolListing(
                                owner.getId(),
                                category,
                                new ListingLocation("100 Le Lai", null, "Ward Ben Thanh", "District 1", "Ho Chi Minh City", "VN"),
                                "Concrete Grinder",
                                "Heavy-duty concrete grinder",
                                450_000L,
                                1_500_000L,
                                "VND",
                                false,
                                List.of()
                );
                listing.submitForReview();
                listing.activate();

                return new Fixture(toolListingRepository.save(listing), owner, renter, secondRenter);
        }

        private IdentityAccount createActiveAccount(String email) {
                IdentityAccount account = identityAccountRepository.save(IdentityAccount.register(email, "hashed-password"));
                account.markEmailVerified();
                account.activate();
                return identityAccountRepository.save(account);
        }

        private record Fixture(
                        ToolListing listing,
                        IdentityAccount owner,
                        IdentityAccount renter,
                        IdentityAccount secondRenter
        ) {
        }
}
