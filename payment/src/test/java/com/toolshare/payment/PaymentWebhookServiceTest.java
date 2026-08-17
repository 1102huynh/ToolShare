package com.toolshare.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolshare.booking.domain.Booking;
import com.toolshare.booking.domain.BookingPolicy;
import com.toolshare.booking.domain.BookingState;
import com.toolshare.booking.domain.BookingStatusHistory;
import com.toolshare.booking.infrastructure.persistence.BookingRepository;
import com.toolshare.booking.infrastructure.persistence.BookingStatusHistoryRepository;
import com.toolshare.common.exception.ResourceNotFoundException;
import com.toolshare.identity.domain.IdentityAccount;
import com.toolshare.identity.infrastructure.persistence.IdentityAccountRepository;
import com.toolshare.listing.domain.Category;
import com.toolshare.listing.domain.ListingLocation;
import com.toolshare.listing.domain.ToolListing;
import com.toolshare.listing.infrastructure.persistence.CategoryRepository;
import com.toolshare.listing.infrastructure.persistence.ToolListingRepository;
import com.toolshare.payment.api.dto.WebhookEventPayload;
import com.toolshare.payment.application.PaymentWebhookService;
import com.toolshare.payment.domain.PaymentOrder;
import com.toolshare.payment.domain.PaymentStatus;
import com.toolshare.payment.domain.PaymentTransactionType;
import com.toolshare.payment.domain.WebhookEventRecord;
import com.toolshare.payment.domain.WebhookEventStatus;
import com.toolshare.payment.infrastructure.persistence.PaymentRepository;
import com.toolshare.payment.infrastructure.persistence.PaymentTransactionRepository;
import com.toolshare.payment.infrastructure.persistence.WebhookEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = PaymentApplication.class)
@AutoConfigureTestDatabase
class PaymentWebhookServiceTest {

    @Autowired
    private PaymentWebhookService paymentWebhookService;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingStatusHistoryRepository bookingStatusHistoryRepository;

    @Autowired
    private IdentityAccountRepository identityAccountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ToolListingRepository toolListingRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Transactional
    void duplicate_event_id_is_processed_once_br_payment_001() throws Exception {
        Fixture fixture = createFixture();
        PaymentOrder payment = advanceTo(fixture.payment(), PaymentStatus.AUTHORIZED);

        String rawBody = payloadJson("evt-" + UUID.randomUUID(), "CAPTURED", payment.getProviderReference(), OffsetDateTime.now());
        String signature = WebhookTestSigning.sign(rawBody);

        WebhookEventRecord first = paymentWebhookService.processWebhook("MVP", rawBody, signature);
        WebhookEventRecord second = paymentWebhookService.processWebhook("MVP", rawBody, signature);

        assertEquals(first.getId(), second.getId());
        assertEquals(WebhookEventStatus.PROCESSED, second.getStatus());
        assertEquals(1, paymentTransactionRepository.findByPaymentIdOrderByOccurredAtAsc(payment.getId()).size());
        assertEquals(PaymentStatus.CAPTURED, paymentRepository.findById(payment.getId()).orElseThrow().getStatus());
    }

    @Test
    @Transactional
    void capture_before_authorization_is_recorded_as_failed_and_state_is_unchanged() throws Exception {
        Fixture fixture = createFixture();
        PaymentOrder payment = fixture.payment(); // still INITIATED

        String rawBody = payloadJson("evt-" + UUID.randomUUID(), "CAPTURED", payment.getProviderReference(), OffsetDateTime.now());
        WebhookEventRecord record = paymentWebhookService.processWebhook("MVP", rawBody, WebhookTestSigning.sign(rawBody));

        assertEquals(WebhookEventStatus.FAILED, record.getStatus());
        assertEquals("unexpected transition, missing intermediate state", record.getOutcomeReason());
        assertEquals(PaymentStatus.INITIATED, paymentRepository.findById(payment.getId()).orElseThrow().getStatus());
        assertTrue(paymentTransactionRepository.findByPaymentIdOrderByOccurredAtAsc(payment.getId()).isEmpty());
    }

    @Test
    @Transactional
    void stale_out_of_order_event_after_payment_already_advanced_is_ignored() throws Exception {
        Fixture fixture = createFixture();
        PaymentOrder payment = advanceTo(fixture.payment(), PaymentStatus.CAPTURED);

        String rawBody = payloadJson("evt-" + UUID.randomUUID(), "AUTHORIZED", payment.getProviderReference(), OffsetDateTime.now());
        WebhookEventRecord record = paymentWebhookService.processWebhook("MVP", rawBody, WebhookTestSigning.sign(rawBody));

        assertEquals(WebhookEventStatus.IGNORED, record.getStatus());
        assertEquals("stale/out-of-order event, payment already advanced past this state", record.getOutcomeReason());
        assertEquals(PaymentStatus.CAPTURED, paymentRepository.findById(payment.getId()).orElseThrow().getStatus());
        assertTrue(paymentTransactionRepository.findByPaymentIdOrderByOccurredAtAsc(payment.getId()).isEmpty());
    }

    @Test
    @Transactional
    void duplicate_effect_delivery_with_different_event_id_is_ignored_as_no_op() throws Exception {
        Fixture fixture = createFixture();
        PaymentOrder payment = advanceTo(fixture.payment(), PaymentStatus.CAPTURED);

        String rawBody = payloadJson("evt-" + UUID.randomUUID(), "CAPTURED", payment.getProviderReference(), OffsetDateTime.now());
        WebhookEventRecord record = paymentWebhookService.processWebhook("MVP", rawBody, WebhookTestSigning.sign(rawBody));

        assertEquals(WebhookEventStatus.IGNORED, record.getStatus());
        assertEquals("already in target state, no-op", record.getOutcomeReason());
        assertTrue(paymentTransactionRepository.findByPaymentIdOrderByOccurredAtAsc(payment.getId()).isEmpty());
    }

    @Test
    @Transactional
    void stale_replayed_timestamp_is_ignored_regardless_of_fresh_event_id() throws Exception {
        Fixture fixture = createFixture();
        PaymentOrder payment = advanceTo(fixture.payment(), PaymentStatus.AUTHORIZED);

        OffsetDateTime staleTimestamp = OffsetDateTime.now().minusMinutes(10);
        String rawBody = payloadJson("evt-" + UUID.randomUUID(), "CAPTURED", payment.getProviderReference(), staleTimestamp);
        WebhookEventRecord record = paymentWebhookService.processWebhook("MVP", rawBody, WebhookTestSigning.sign(rawBody));

        assertEquals(WebhookEventStatus.IGNORED, record.getStatus());
        assertEquals("stale event outside replay tolerance", record.getOutcomeReason());
        assertEquals(PaymentStatus.AUTHORIZED, paymentRepository.findById(payment.getId()).orElseThrow().getStatus());
    }

    @Test
    @Transactional
    void unresolvable_provider_reference_is_not_found_and_writes_no_row() throws Exception {
        String eventId = "evt-" + UUID.randomUUID();
        String rawBody = payloadJson(eventId, "CAPTURED", "mvp-" + UUID.randomUUID(), OffsetDateTime.now());

        assertThrows(ResourceNotFoundException.class,
                () -> paymentWebhookService.processWebhook("MVP", rawBody, WebhookTestSigning.sign(rawBody)));

        assertTrue(webhookEventRepository.findByProviderNameAndProviderEventId("MVP", eventId).isEmpty());
    }

    @Test
    @Transactional
    void happy_path_authorized_then_captured_reaches_captured_with_two_ledger_rows() throws Exception {
        Fixture fixture = createFixture();
        PaymentOrder payment = fixture.payment();

        String authorizedBody = payloadJson("evt-" + UUID.randomUUID(), "AUTHORIZED", payment.getProviderReference(), OffsetDateTime.now());
        WebhookEventRecord authorizedRecord = paymentWebhookService.processWebhook("MVP", authorizedBody, WebhookTestSigning.sign(authorizedBody));
        assertEquals(WebhookEventStatus.PROCESSED, authorizedRecord.getStatus());

        // Negative check (§5 boundary): an AUTHORIZED-only transition must never touch booking.
        assertEquals(BookingState.PAYMENT_PENDING, bookingRepository.findById(fixture.booking().getId()).orElseThrow().getState());
        assertTrue(bookingStatusHistoryRepository.findByBookingIdOrderByChangedAtAsc(fixture.booking().getId()).isEmpty());

        String capturedBody = payloadJson("evt-" + UUID.randomUUID(), "CAPTURED", payment.getProviderReference(), OffsetDateTime.now());
        WebhookEventRecord capturedRecord = paymentWebhookService.processWebhook("MVP", capturedBody, WebhookTestSigning.sign(capturedBody));
        assertEquals(WebhookEventStatus.PROCESSED, capturedRecord.getStatus());

        assertEquals(PaymentStatus.CAPTURED, paymentRepository.findById(payment.getId()).orElseThrow().getStatus());
        assertEquals(2, paymentTransactionRepository.findByPaymentIdOrderByOccurredAtAsc(payment.getId()).size());
        assertEquals(2, webhookEventRepository.findAll().stream().filter(r -> r.getPaymentId().equals(payment.getId())).count());
    }

    @Test
    @Transactional
    void refunded_event_from_captured_is_the_first_exercise_of_that_transition() throws Exception {
        Fixture fixture = createFixture();
        PaymentOrder payment = advanceTo(fixture.payment(), PaymentStatus.CAPTURED);

        String rawBody = payloadJson("evt-" + UUID.randomUUID(), "REFUNDED", payment.getProviderReference(), OffsetDateTime.now());
        WebhookEventRecord record = paymentWebhookService.processWebhook("MVP", rawBody, WebhookTestSigning.sign(rawBody));

        assertEquals(WebhookEventStatus.PROCESSED, record.getStatus());
        assertEquals(PaymentStatus.REFUNDED, paymentRepository.findById(payment.getId()).orElseThrow().getStatus());
        List<com.toolshare.payment.domain.PaymentTransaction> ledger =
                paymentTransactionRepository.findByPaymentIdOrderByOccurredAtAsc(payment.getId());
        assertEquals(1, ledger.size());
        assertEquals(PaymentTransactionType.REFUND, ledger.get(0).getTransactionType());
    }

    @Test
    @Transactional
    void partially_refunded_event_from_captured_is_processed() throws Exception {
        Fixture fixture = createFixture();
        PaymentOrder payment = advanceTo(fixture.payment(), PaymentStatus.CAPTURED);

        String rawBody = payloadJson("evt-" + UUID.randomUUID(), "PARTIALLY_REFUNDED", payment.getProviderReference(), OffsetDateTime.now());
        WebhookEventRecord record = paymentWebhookService.processWebhook("MVP", rawBody, WebhookTestSigning.sign(rawBody));

        assertEquals(WebhookEventStatus.PROCESSED, record.getStatus());
        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, paymentRepository.findById(payment.getId()).orElseThrow().getStatus());
    }

    /**
     * T-012 review fix: deliberately NOT {@code @Transactional}. {@code confirmAfterPayment}
     * now runs {@code REQUIRES_NEW} (see {@code BookingService}), which opens a separate
     * physical transaction/connection — one that cannot see this test's fixture data if
     * that data is still sitting in an uncommitted, test-managed outer transaction. Real
     * commits are required here, same as {@code PaymentWebhookControllerIntegrationTest}'s
     * equivalent HTTP-level scenario; scoped-by-id assertions keep this safe to run
     * alongside the rest of this class's {@code @Transactional} (rollback) tests.
     */
    @Test
    void captured_webhook_confirms_booking_still_in_payment_pending() throws Exception {
        Fixture fixture = createFixture();
        PaymentOrder payment = advanceTo(fixture.payment(), PaymentStatus.AUTHORIZED);

        String rawBody = payloadJson("evt-" + UUID.randomUUID(), "CAPTURED", payment.getProviderReference(), OffsetDateTime.now());
        WebhookEventRecord record = paymentWebhookService.processWebhook("MVP", rawBody, WebhookTestSigning.sign(rawBody));

        assertEquals(WebhookEventStatus.PROCESSED, record.getStatus());
        assertNull(record.getOutcomeReason());

        Booking confirmedBooking = bookingRepository.findById(fixture.booking().getId()).orElseThrow();
        assertEquals(BookingState.CONFIRMED, confirmedBooking.getState());

        List<BookingStatusHistory> history = bookingStatusHistoryRepository.findByBookingIdOrderByChangedAtAsc(fixture.booking().getId());
        assertEquals(1, history.size());
        assertEquals(BookingState.CONFIRMED, history.get(0).getNewState());
        assertEquals("PAYMENT_CAPTURED", history.get(0).getReason());
        assertNull(history.get(0).getActorAccountId());
    }

    /**
     * T-012 review fix: also deliberately NOT {@code @Transactional} — same reason as
     * {@link #captured_webhook_confirms_booking_still_in_payment_pending()}. Without a
     * real commit here, the REQUIRES_NEW {@code confirmAfterPayment} call would fail
     * with "Booking not found" (uncommitted fixture) rather than the intended
     * "booking already cancelled" business rejection, silently testing the wrong thing.
     */
    @Test
    void captured_webhook_still_captures_payment_when_booking_confirm_fails() throws Exception {
        Fixture fixture = createFixture();
        PaymentOrder payment = advanceTo(fixture.payment(), PaymentStatus.AUTHORIZED);

        // Booking independently cancelled by the renter while payment was mid-flight.
        Booking booking = fixture.booking();
        booking.cancel();
        bookingRepository.save(booking);

        String rawBody = payloadJson("evt-" + UUID.randomUUID(), "CAPTURED", payment.getProviderReference(), OffsetDateTime.now());
        WebhookEventRecord record = paymentWebhookService.processWebhook("MVP", rawBody, WebhookTestSigning.sign(rawBody));

        assertEquals(WebhookEventStatus.PROCESSED, record.getStatus());
        assertTrue(record.getOutcomeReason() != null && record.getOutcomeReason().startsWith("payment captured, booking confirm skipped:"));
        assertEquals(PaymentStatus.CAPTURED, paymentRepository.findById(payment.getId()).orElseThrow().getStatus());
        assertEquals(BookingState.CANCELLED, bookingRepository.findById(booking.getId()).orElseThrow().getState());
    }

    private String payloadJson(String eventId, String eventType, String providerReference, OffsetDateTime occurredAt) throws Exception {
        return objectMapper.writeValueAsString(new WebhookEventPayload(eventId, eventType, providerReference, occurredAt));
    }

    private PaymentOrder advanceTo(PaymentOrder payment, PaymentStatus target) {
        if (target == PaymentStatus.INITIATED) {
            return payment;
        }
        payment.authorize();
        if (target == PaymentStatus.AUTHORIZED) {
            return paymentRepository.save(payment);
        }
        payment.capture();
        if (target == PaymentStatus.CAPTURED) {
            return paymentRepository.save(payment);
        }
        throw new IllegalArgumentException("Unsupported fixture target status: " + target);
    }

    private Fixture createFixture() {
        IdentityAccount owner = createActiveAccount("owner-webhook-" + UUID.randomUUID() + "@example.com");
        IdentityAccount renter = createActiveAccount("renter-webhook-" + UUID.randomUUID() + "@example.com");

        Category category = categoryRepository.save(new Category("webhook-cat-" + UUID.randomUUID(), "Webhook Category"));

        ToolListing listing = new ToolListing(
                owner.getId(),
                category,
                new ListingLocation("1 Le Duan", null, "Ben Nghe", "District 1", "Ho Chi Minh City", "VN"),
                "Impact Driver",
                "Cordless impact driver",
                200_000L,
                500_000L,
                "VND",
                true,
                List.of()
        );
        listing.submitForReview();
        listing.activate();
        ToolListing savedListing = toolListingRepository.save(listing);

        LocalDateTime start = LocalDateTime.now().plusDays(2).withMinute(0).withSecond(0).withNano(0);
        Booking booking = bookingRepository.save(Booking.create(
                savedListing.getId(), renter.getId(), owner.getId(), start, start.plusDays(2), BookingPolicy.INSTANT
        ));

        String providerReference = "mvp-" + UUID.randomUUID();
        PaymentOrder payment = PaymentOrder.initiate(booking.getId(), renter.getId(), 400_000L, "VND", "MVP");
        payment.attachProviderReference(providerReference);
        PaymentOrder savedPayment = paymentRepository.save(payment);

        return new Fixture(booking, savedPayment);
    }

    private IdentityAccount createActiveAccount(String email) {
        IdentityAccount account = identityAccountRepository.save(IdentityAccount.register(email, "hashed-password"));
        account.markEmailVerified();
        account.activate();
        return identityAccountRepository.save(account);
    }

    private record Fixture(Booking booking, PaymentOrder payment) {
    }
}
