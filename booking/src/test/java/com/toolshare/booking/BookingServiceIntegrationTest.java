package com.toolshare.booking;

import com.toolshare.booking.application.BookingService;
import com.toolshare.booking.domain.Booking;
import com.toolshare.booking.domain.BookingPolicy;
import com.toolshare.booking.domain.BookingState;
import com.toolshare.booking.domain.BookingStatusHistory;
import com.toolshare.booking.infrastructure.persistence.BookingStatusHistoryRepository;
import com.toolshare.common.exception.ConflictException;
import com.toolshare.common.exception.ForbiddenException;
import com.toolshare.common.exception.ValidationException;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = BookingApplication.class)
@AutoConfigureTestDatabase
class BookingServiceIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private IdentityAccountRepository identityAccountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ToolListingRepository toolListingRepository;

    @Autowired
    private BookingStatusHistoryRepository bookingStatusHistoryRepository;

    @Test
    @Transactional
    void create_booking_persists_booking_and_initial_history() {
        Fixture fixture = createFixture(true);

        Booking booking = bookingService.createBooking(
                fixture.listing.getId(),
                fixture.renter.getId(),
                fixture.start,
                fixture.end,
                BookingPolicy.OWNER_APPROVAL
        );

        assertEquals(BookingState.REQUESTED, booking.getState());

        Booking reloaded = bookingService.getBooking(booking.getId());
        assertEquals(fixture.listing.getId(), reloaded.getListingId());

        List<BookingStatusHistory> history = bookingStatusHistoryRepository.findByBookingIdOrderByChangedAtAsc(booking.getId());
        assertEquals(1, history.size());
        assertEquals(null, history.get(0).getPreviousState());
        assertEquals(BookingState.REQUESTED, history.get(0).getNewState());
    }

    @Test
    @Transactional
    void create_booking_rejects_invalid_time_range() {
        Fixture fixture = createFixture(true);

        assertThrows(ValidationException.class, () -> bookingService.createBooking(
                fixture.listing.getId(),
                fixture.renter.getId(),
                fixture.end,
                fixture.start,
                BookingPolicy.OWNER_APPROVAL
        ));
    }

    @Test
    @Transactional
    void create_booking_rejects_start_time_in_the_past() {
        Fixture fixture = createFixture(true);

        assertThrows(ValidationException.class, () -> bookingService.createBooking(
                fixture.listing.getId(),
                fixture.renter.getId(),
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(2),
                BookingPolicy.OWNER_APPROVAL
        ));
    }

    @Test
    @Transactional
    void create_booking_rejects_inactive_listing() {
        Fixture fixture = createFixture(false);

        assertEquals(ListingStatus.DRAFT, fixture.listing.getStatus());
        assertThrows(ConflictException.class, () -> bookingService.createBooking(
                fixture.listing.getId(),
                fixture.renter.getId(),
                fixture.start,
                fixture.end,
                BookingPolicy.OWNER_APPROVAL
        ));
    }

    @Test
    @Transactional
    void create_booking_rejects_overlapping_active_booking() {
        Fixture fixture = createFixture(true);

        bookingService.createBooking(
                fixture.listing.getId(),
                fixture.renter.getId(),
                fixture.start,
                fixture.end,
                BookingPolicy.OWNER_APPROVAL
        );

        assertThrows(ConflictException.class, () -> bookingService.createBooking(
                fixture.listing.getId(),
                fixture.secondRenter.getId(),
                fixture.start.plusMinutes(30),
                fixture.end.plusMinutes(30),
                BookingPolicy.OWNER_APPROVAL
        ));
    }

    @Test
    @Transactional
    void approval_policy_routes_to_requested_and_instant_routes_to_payment_pending() {
        Fixture fixture = createFixture(true);

        Booking requested = bookingService.createBooking(
                fixture.listing.getId(),
                fixture.renter.getId(),
                fixture.start,
                fixture.end,
                BookingPolicy.OWNER_APPROVAL
        );

        Booking instant = bookingService.createBooking(
                fixture.listing.getId(),
                fixture.secondRenter.getId(),
                fixture.end.plusHours(2),
                fixture.end.plusHours(4),
                BookingPolicy.INSTANT
        );

        assertEquals(BookingState.REQUESTED, requested.getState());
        assertEquals(BookingState.PAYMENT_PENDING, instant.getState());
    }

    @Test
    @Transactional
    void approve_reject_cancel_and_expire_follow_lifecycle_rules() {
        Fixture fixture = createFixture(true);

        Booking booking = bookingService.createBooking(
                fixture.listing.getId(),
                fixture.renter.getId(),
                fixture.start,
                fixture.end,
                BookingPolicy.OWNER_APPROVAL
        );

        Booking approved = bookingService.approveBooking(booking.getId(), fixture.owner.getId());
        assertEquals(BookingState.PAYMENT_PENDING, approved.getState());

        assertThrows(ConflictException.class, () -> bookingService.rejectBooking(booking.getId(), fixture.owner.getId(), "late response"));

        Booking cancelled = bookingService.cancelBooking(booking.getId(), fixture.renter.getId(), "changed plans");
        assertEquals(BookingState.CANCELLED, cancelled.getState());

        Booking expirable = bookingService.createBooking(
                fixture.listing.getId(),
                fixture.secondRenter.getId(),
                fixture.end.plusHours(5),
                fixture.end.plusHours(7),
                BookingPolicy.OWNER_APPROVAL
        );

        Booking expired = bookingService.expirePendingBooking(expirable.getId());
        assertEquals(BookingState.EXPIRED, expired.getState());
    }

    @Test
    @Transactional
    void owner_decision_requires_owner_actor_and_cancel_requires_participant_actor() {
        Fixture fixture = createFixture(true);

        Booking booking = bookingService.createBooking(
                fixture.listing.getId(),
                fixture.renter.getId(),
                fixture.start,
                fixture.end,
                BookingPolicy.OWNER_APPROVAL
        );

        assertThrows(ForbiddenException.class, () -> bookingService.approveBooking(booking.getId(), fixture.renter.getId()));

        IdentityAccount outsider = createActiveAccount("outsider@example.com");
        assertThrows(ForbiddenException.class, () -> bookingService.cancelBooking(booking.getId(), outsider.getId(), "not participant"));
    }

    @Test
    @Transactional
    void booking_history_is_append_only_through_transitions() {
        Fixture fixture = createFixture(true);

        Booking booking = bookingService.createBooking(
                fixture.listing.getId(),
                fixture.renter.getId(),
                fixture.start,
                fixture.end,
                BookingPolicy.OWNER_APPROVAL
        );

        bookingService.approveBooking(booking.getId(), fixture.owner.getId());
        bookingService.cancelBooking(booking.getId(), fixture.renter.getId(), "cancel");

        List<BookingStatusHistory> history = bookingStatusHistoryRepository.findByBookingIdOrderByChangedAtAsc(booking.getId());
        assertEquals(3, history.size());
        assertEquals(BookingState.REQUESTED, history.get(0).getNewState());
        assertEquals(BookingState.PAYMENT_PENDING, history.get(1).getNewState());
        assertEquals(BookingState.CANCELLED, history.get(2).getNewState());
        assertTrue(history.get(0).getChangedAt().isBefore(history.get(2).getChangedAt()) || history.get(0).getChangedAt().isEqual(history.get(2).getChangedAt()));
    }

    private Fixture createFixture(boolean activateListing) {
        IdentityAccount owner = createActiveAccount("owner-" + UUID.randomUUID() + "@example.com");
        IdentityAccount renter = createActiveAccount("renter-" + UUID.randomUUID() + "@example.com");
        IdentityAccount secondRenter = createActiveAccount("renter2-" + UUID.randomUUID() + "@example.com");

        Category category = categoryRepository.save(new Category("booking-cat-" + UUID.randomUUID(), "Booking Category"));

        ToolListing listing = new ToolListing(
                owner.getId(),
                category,
                new ListingLocation("123 Nguyen Trai", null, "Ward 1", "District 1", "Ho Chi Minh City", "VN"),
                "Rotary Hammer",
                "Professional rotary hammer",
                200_000L,
                1_000_000L,
                "VND",
                true,
                List.of()
        );

        if (activateListing) {
            listing.submitForReview();
            listing.activate();
        }

        ToolListing savedListing = toolListingRepository.save(listing);

        LocalDateTime start = LocalDateTime.now().plusDays(2).withMinute(0).withSecond(0).withNano(0);
        return new Fixture(savedListing, owner, renter, secondRenter, start, start.plusHours(4));
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
            IdentityAccount secondRenter,
            LocalDateTime start,
            LocalDateTime end
    ) {
    }
}
