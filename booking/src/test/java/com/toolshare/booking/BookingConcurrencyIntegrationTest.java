package com.toolshare.booking;

import com.toolshare.booking.application.BookingService;
import com.toolshare.booking.domain.Booking;
import com.toolshare.booking.domain.BookingPolicy;
import com.toolshare.booking.domain.BookingState;
import com.toolshare.booking.infrastructure.persistence.BookingRepository;
import com.toolshare.common.exception.ConflictException;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression coverage for T-010: closes the race window between the overlap
 * check and the booking insert in {@link BookingService#createBooking}.
 *
 * These tests use real, independently-executed transactions (one per thread,
 * via the {@code @Transactional} proxy on {@link BookingService}) synchronized
 * with a {@link CyclicBarrier} so both requests reach {@code createBooking}
 * as close to simultaneously as the JVM scheduler allows. Without the
 * listing-row pessimistic lock introduced for T-010, both attempts can
 * observe "no conflict" during their overlap check and both commit,
 * persisting two overlapping active bookings for the same listing.
 */
@SpringBootTest(classes = BookingApplication.class)
@AutoConfigureTestDatabase
class BookingConcurrencyIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private IdentityAccountRepository identityAccountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ToolListingRepository toolListingRepository;

    @Test
    void two_concurrent_requests_for_the_same_listing_and_time_window_yield_exactly_one_winner() throws Exception {
        Fixture fixture = createFixture();

        List<AttemptResult> results = runConcurrently(
                fixture,
                fixture.start, fixture.end,
                fixture.start, fixture.end
        );

        assertExactlyOneWinnerOneConflict(results);
        assertSinglePersistedActiveBooking(fixture.listing.getId(), fixture.start, fixture.end);
    }

    @Test
    void two_concurrent_requests_for_overlapping_but_distinct_windows_yield_exactly_one_winner() throws Exception {
        Fixture fixture = createFixture();

        LocalDateTime aStart = fixture.start;
        LocalDateTime aEnd = fixture.start.plusHours(2);
        LocalDateTime bStart = fixture.start.plusHours(1);
        LocalDateTime bEnd = fixture.start.plusHours(3);

        List<AttemptResult> results = runConcurrently(fixture, aStart, aEnd, bStart, bEnd);

        assertExactlyOneWinnerOneConflict(results);
        assertSinglePersistedActiveBooking(fixture.listing.getId(), aStart, bEnd);
    }

    private List<AttemptResult> runConcurrently(
            Fixture fixture,
            LocalDateTime aStart,
            LocalDateTime aEnd,
            LocalDateTime bStart,
            LocalDateTime bEnd
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            Callable<AttemptResult> attemptA = attemptOf(barrier, fixture.listing.getId(), fixture.renter.getId(), aStart, aEnd);
            Callable<AttemptResult> attemptB = attemptOf(barrier, fixture.listing.getId(), fixture.secondRenter.getId(), bStart, bEnd);

            Future<AttemptResult> futureA = executor.submit(attemptA);
            Future<AttemptResult> futureB = executor.submit(attemptB);

            return List.of(futureA.get(30, TimeUnit.SECONDS), futureB.get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<AttemptResult> attemptOf(
            CyclicBarrier barrier,
            UUID listingId,
            UUID renterId,
            LocalDateTime start,
            LocalDateTime end
    ) {
        return () -> {
            barrier.await(30, TimeUnit.SECONDS);
            try {
                Booking booking = bookingService.createBooking(listingId, renterId, start, end, BookingPolicy.OWNER_APPROVAL);
                return AttemptResult.success(booking);
            } catch (ConflictException ex) {
                return AttemptResult.conflict(ex);
            }
        };
    }

    private void assertExactlyOneWinnerOneConflict(List<AttemptResult> results) {
        long successes = results.stream().filter(AttemptResult::success).count();
        long conflicts = results.stream().filter(r -> !r.success()).count();

        assertEquals(1, successes, "exactly one concurrent booking attempt should succeed: " + results);
        assertEquals(1, conflicts, "exactly one concurrent booking attempt should be rejected as a conflict: " + results);
    }

    private void assertSinglePersistedActiveBooking(UUID listingId, LocalDateTime windowStart, LocalDateTime windowEnd) {
        List<BookingState> activeStates = List.copyOf(BookingState.overlapBlockingStates());
        List<Booking> persistedActive = bookingRepository.findOverlappingByListingAndStates(
                listingId,
                windowStart,
                windowEnd,
                activeStates
        );
        assertEquals(1, persistedActive.size(), "exactly one active booking should be persisted for the listing");
    }

    private Fixture createFixture() {
        IdentityAccount owner = createActiveAccount("owner-concurrency-" + UUID.randomUUID() + "@example.com");
        IdentityAccount renter = createActiveAccount("renter-concurrency-" + UUID.randomUUID() + "@example.com");
        IdentityAccount secondRenter = createActiveAccount("renter2-concurrency-" + UUID.randomUUID() + "@example.com");

        Category category = categoryRepository.save(new Category("booking-concurrency-" + UUID.randomUUID(), "Booking Concurrency"));

        ToolListing listing = new ToolListing(
                owner.getId(),
                category,
                new ListingLocation("77 Pasteur", null, "Ward 6", "District 3", "Ho Chi Minh City", "VN"),
                "Cordless Drill",
                "18V cordless drill with two batteries",
                150_000L,
                800_000L,
                "VND",
                true,
                List.of()
        );
        listing.submitForReview();
        listing.activate();

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

    private record AttemptResult(boolean success, Booking booking, ConflictException conflict) {
        static AttemptResult success(Booking booking) {
            return new AttemptResult(true, booking, null);
        }

        static AttemptResult conflict(ConflictException ex) {
            return new AttemptResult(false, null, ex);
        }
    }
}
