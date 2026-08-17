package com.toolshare.payment;

import com.toolshare.booking.domain.Booking;
import com.toolshare.booking.domain.BookingPolicy;
import com.toolshare.booking.infrastructure.persistence.BookingRepository;
import com.toolshare.common.exception.ConflictException;
import com.toolshare.common.exception.ForbiddenException;
import com.toolshare.identity.domain.IdentityAccount;
import com.toolshare.identity.infrastructure.persistence.IdentityAccountRepository;
import com.toolshare.listing.domain.Category;
import com.toolshare.listing.domain.ListingLocation;
import com.toolshare.listing.domain.ToolListing;
import com.toolshare.listing.infrastructure.persistence.CategoryRepository;
import com.toolshare.listing.infrastructure.persistence.ToolListingRepository;
import com.toolshare.payment.application.PaymentService;
import com.toolshare.payment.domain.PaymentOrder;
import com.toolshare.payment.domain.PaymentStatus;
import com.toolshare.payment.infrastructure.persistence.PaymentTransactionRepository;
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

@SpringBootTest(classes = PaymentApplication.class)
@AutoConfigureTestDatabase
class PaymentServiceIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private IdentityAccountRepository identityAccountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ToolListingRepository toolListingRepository;

    @Test
    @Transactional
    void happy_path_reaches_captured_with_three_ledger_rows_and_correct_amount() {
        Fixture fixture = createFixture(200_000L);
        Booking booking = createBooking(fixture, fixture.start, fixture.start.plusDays(2));

        PaymentOrder created = paymentService.createPaymentIntent(booking.getId(), fixture.renter.getId());
        assertEquals(PaymentStatus.INITIATED, created.getStatus());
        assertEquals(400_000L, created.getAmountMinor());

        PaymentOrder authorized = paymentService.authorizePayment(created.getId());
        assertEquals(PaymentStatus.AUTHORIZED, authorized.getStatus());

        PaymentOrder captured = paymentService.capturePayment(created.getId());
        assertEquals(PaymentStatus.CAPTURED, captured.getStatus());

        List<com.toolshare.payment.domain.PaymentTransaction> ledger =
                paymentTransactionRepository.findByPaymentIdOrderByOccurredAtAsc(created.getId());
        assertEquals(3, ledger.size());
    }

    @Test
    @Transactional
    void capture_before_authorization_is_rejected_and_state_is_unchanged() {
        Fixture fixture = createFixture(150_000L);
        Booking booking = createBooking(fixture, fixture.start, fixture.start.plusDays(1));
        PaymentOrder created = paymentService.createPaymentIntent(booking.getId(), fixture.renter.getId());

        assertThrows(ConflictException.class, () -> paymentService.capturePayment(created.getId()));

        PaymentOrder reloaded = paymentService.getPayment(created.getId());
        assertEquals(PaymentStatus.INITIATED, reloaded.getStatus());
    }

    @Test
    @Transactional
    void non_renter_actor_cannot_create_payment_intent() {
        Fixture fixture = createFixture(150_000L);
        Booking booking = createBooking(fixture, fixture.start, fixture.start.plusDays(1));

        assertThrows(ForbiddenException.class, () -> paymentService.createPaymentIntent(booking.getId(), fixture.owner.getId()));
    }

    @Test
    @Transactional
    void duplicate_payment_intent_for_same_booking_is_rejected() {
        Fixture fixture = createFixture(150_000L);
        Booking booking = createBooking(fixture, fixture.start, fixture.start.plusDays(1));
        paymentService.createPaymentIntent(booking.getId(), fixture.renter.getId());

        assertThrows(ConflictException.class, () -> paymentService.createPaymentIntent(booking.getId(), fixture.renter.getId()));
    }

    @Test
    @Transactional
    void each_transition_appends_exactly_one_ledger_row() {
        Fixture fixture = createFixture(150_000L);
        Booking booking = createBooking(fixture, fixture.start, fixture.start.plusDays(1));

        PaymentOrder created = paymentService.createPaymentIntent(booking.getId(), fixture.renter.getId());
        assertEquals(1, paymentTransactionRepository.findByPaymentIdOrderByOccurredAtAsc(created.getId()).size());

        paymentService.authorizePayment(created.getId());
        assertEquals(2, paymentTransactionRepository.findByPaymentIdOrderByOccurredAtAsc(created.getId()).size());

        paymentService.capturePayment(created.getId());
        assertEquals(3, paymentTransactionRepository.findByPaymentIdOrderByOccurredAtAsc(created.getId()).size());
    }

    @Test
    @Transactional
    void zero_daily_rate_listing_produces_zero_amount_payment() {
        Fixture fixture = createFixture(0L);
        Booking booking = createBooking(fixture, fixture.start, fixture.start.plusDays(3));

        PaymentOrder created = paymentService.createPaymentIntent(booking.getId(), fixture.renter.getId());

        assertEquals(0L, created.getAmountMinor());
    }

    @Test
    @Transactional
    void cancel_payment_transitions_to_cancelled_and_appends_ledger_row() {
        Fixture fixture = createFixture(150_000L);
        Booking booking = createBooking(fixture, fixture.start, fixture.start.plusDays(1));
        PaymentOrder created = paymentService.createPaymentIntent(booking.getId(), fixture.renter.getId());

        PaymentOrder cancelled = paymentService.cancelPayment(created.getId());

        assertEquals(PaymentStatus.CANCELLED, cancelled.getStatus());
        assertEquals(2, paymentTransactionRepository.findByPaymentIdOrderByOccurredAtAsc(created.getId()).size());
    }

    @Test
    @Transactional
    void fail_payment_transitions_to_failed_and_appends_ledger_row() {
        Fixture fixture = createFixture(150_000L);
        Booking booking = createBooking(fixture, fixture.start, fixture.start.plusDays(1));
        PaymentOrder created = paymentService.createPaymentIntent(booking.getId(), fixture.renter.getId());

        PaymentOrder failed = paymentService.failPayment(created.getId(), "support-initiated failure");

        assertEquals(PaymentStatus.FAILED, failed.getStatus());
        assertEquals(2, paymentTransactionRepository.findByPaymentIdOrderByOccurredAtAsc(created.getId()).size());
    }

    @Test
    @Transactional
    void get_payment_by_booking_returns_the_associated_payment() {
        Fixture fixture = createFixture(150_000L);
        Booking booking = createBooking(fixture, fixture.start, fixture.start.plusDays(1));
        PaymentOrder created = paymentService.createPaymentIntent(booking.getId(), fixture.renter.getId());

        PaymentOrder found = paymentService.getPaymentByBooking(booking.getId());

        assertEquals(created.getId(), found.getId());
    }

    private Booking createBooking(Fixture fixture, LocalDateTime start, LocalDateTime end) {
        return bookingRepository.save(Booking.create(
                fixture.listing.getId(),
                fixture.renter.getId(),
                fixture.owner.getId(),
                start,
                end,
                BookingPolicy.INSTANT
        ));
    }

    private Fixture createFixture(long dailyRateAmount) {
        IdentityAccount owner = createActiveAccount("owner-payment-" + UUID.randomUUID() + "@example.com");
        IdentityAccount renter = createActiveAccount("renter-payment-" + UUID.randomUUID() + "@example.com");

        Category category = categoryRepository.save(new Category("payment-cat-" + UUID.randomUUID(), "Payment Category"));

        ToolListing listing = new ToolListing(
                owner.getId(),
                category,
                new ListingLocation("1 Le Duan", null, "Ben Nghe", "District 1", "Ho Chi Minh City", "VN"),
                "Impact Driver",
                "Cordless impact driver",
                dailyRateAmount,
                500_000L,
                "VND",
                true,
                List.of()
        );
        listing.submitForReview();
        listing.activate();
        ToolListing savedListing = toolListingRepository.save(listing);

        LocalDateTime start = LocalDateTime.now().plusDays(2).withMinute(0).withSecond(0).withNano(0);
        return new Fixture(savedListing, owner, renter, start);
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
            LocalDateTime start
    ) {
    }
}
