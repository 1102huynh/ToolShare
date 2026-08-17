package com.toolshare.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolshare.booking.domain.Booking;
import com.toolshare.booking.domain.BookingPolicy;
import com.toolshare.booking.domain.BookingState;
import com.toolshare.booking.infrastructure.persistence.BookingRepository;
import com.toolshare.booking.infrastructure.persistence.BookingStatusHistoryRepository;
import com.toolshare.identity.domain.IdentityAccount;
import com.toolshare.identity.infrastructure.persistence.IdentityAccountRepository;
import com.toolshare.listing.domain.Category;
import com.toolshare.listing.domain.ListingLocation;
import com.toolshare.listing.domain.ToolListing;
import com.toolshare.listing.infrastructure.persistence.CategoryRepository;
import com.toolshare.listing.infrastructure.persistence.ToolListingRepository;
import com.toolshare.payment.api.dto.WebhookEventPayload;
import com.toolshare.payment.domain.PaymentOrder;
import com.toolshare.payment.domain.PaymentStatus;
import com.toolshare.payment.domain.WebhookEventRecord;
import com.toolshare.payment.domain.WebhookEventStatus;
import com.toolshare.payment.infrastructure.persistence.PaymentRepository;
import com.toolshare.payment.infrastructure.persistence.PaymentTransactionRepository;
import com.toolshare.payment.infrastructure.persistence.WebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * First MockMvc-level test for {@code payment} (T-012 plan §16 item 3) —
 * establishes the same {@code @SpringBootTest} + {@code @AutoConfigureMockMvc}
 * convention already used in {@code identity}/{@code listing}
 * (e.g. {@code ListingApiIntegrationTest}), including its {@code @BeforeEach}
 * clean-state pattern rather than per-test {@code @Transactional} rollback.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentWebhookControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private BookingStatusHistoryRepository bookingStatusHistoryRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ToolListingRepository toolListingRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private IdentityAccountRepository identityAccountRepository;

    @BeforeEach
    void cleanState() {
        webhookEventRepository.deleteAll();
        paymentTransactionRepository.deleteAll();
        paymentRepository.deleteAll();
        bookingStatusHistoryRepository.deleteAll();
        bookingRepository.deleteAll();
        toolListingRepository.deleteAll();
        categoryRepository.deleteAll();
        identityAccountRepository.deleteAll();
    }

    @Test
    void valid_signature_and_new_event_returns_200() throws Exception {
        PaymentOrder payment = createPaymentFixture();
        String rawBody = payloadJson("evt-" + UUID.randomUUID(), "AUTHORIZED", payment.getProviderReference(), OffsetDateTime.now());

        mockMvc.perform(post("/api/v1/payments/webhooks/MVP")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBody)
                        .header("X-Webhook-Signature", WebhookTestSigning.sign(rawBody)))
                .andExpect(status().isOk());
    }

    @Test
    void invalid_signature_returns_401_with_no_leaked_internal_detail() throws Exception {
        PaymentOrder payment = createPaymentFixture();
        String rawBody = payloadJson("evt-" + UUID.randomUUID(), "AUTHORIZED", payment.getProviderReference(), OffsetDateTime.now());

        mockMvc.perform(post("/api/v1/payments/webhooks/MVP")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBody)
                        .header("X-Webhook-Signature", "sha256=" + "0".repeat(64)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Invalid webhook signature"));

        assertEquals(0, webhookEventRepository.findAll().size());
    }

    @Test
    void malformed_json_body_correctly_signed_over_its_own_bytes_returns_400() throws Exception {
        String rawBody = "{this is not valid json";

        mockMvc.perform(post("/api/v1/payments/webhooks/MVP")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBody)
                        .header("X-Webhook-Signature", WebhookTestSigning.sign(rawBody)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknown_provider_reference_returns_404() throws Exception {
        String rawBody = payloadJson("evt-" + UUID.randomUUID(), "AUTHORIZED", "mvp-" + UUID.randomUUID(), OffsetDateTime.now());

        mockMvc.perform(post("/api/v1/payments/webhooks/MVP")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBody)
                        .header("X-Webhook-Signature", WebhookTestSigning.sign(rawBody)))
                .andExpect(status().isNotFound());
    }

    @Test
    void replayed_exact_same_request_is_idempotent_end_to_end() throws Exception {
        PaymentOrder payment = createPaymentFixture();
        String rawBody = payloadJson("evt-" + UUID.randomUUID(), "AUTHORIZED", payment.getProviderReference(), OffsetDateTime.now());
        String signature = WebhookTestSigning.sign(rawBody);

        mockMvc.perform(post("/api/v1/payments/webhooks/MVP")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBody)
                        .header("X-Webhook-Signature", signature))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/payments/webhooks/MVP")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBody)
                        .header("X-Webhook-Signature", signature))
                .andExpect(status().isOk());

        assertEquals(1, paymentTransactionRepository.findByPaymentIdOrderByOccurredAtAsc(payment.getId()).size());
        assertEquals(1, webhookEventRepository.findAll().size());
    }

    /**
     * T-012 review fix: end-to-end HTTP-level regression for the §5 booking-confirm-
     * failure sub-case — distinct from (and necessary alongside) the service-level
     * test of the same scenario, because this class does NOT wrap test methods in
     * {@code @Transactional} (see the class Javadoc), so the request commits through
     * the real transaction boundaries. A service-level test wrapped in a rollback-
     * always test transaction cannot observe an {@code UnexpectedRollbackException}
     * at the real commit boundary; this test can and does, and is what proves the
     * {@code REQUIRES_NEW} fix on {@code BookingService.confirmAfterPayment}.
     */
    @Test
    void captured_webhook_still_commits_payment_capture_when_booking_confirm_fails() throws Exception {
        BookingPaymentFixture fixture = createBookingAndPaymentFixture();
        PaymentOrder authorizedPayment = advanceToAuthorized(fixture.payment());

        // Booking independently cancelled by the renter while payment was mid-flight.
        Booking booking = fixture.booking();
        booking.cancel();
        bookingRepository.save(booking);

        String rawBody = payloadJson("evt-" + UUID.randomUUID(), "CAPTURED", authorizedPayment.getProviderReference(), OffsetDateTime.now());
        String signature = WebhookTestSigning.sign(rawBody);

        mockMvc.perform(post("/api/v1/payments/webhooks/MVP")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBody)
                        .header("X-Webhook-Signature", signature))
                .andExpect(status().isOk());

        // Re-read everything fresh from the DB — these are real, separately-committed
        // reads, not the same in-memory transaction the request ran in.
        PaymentOrder reloadedPayment = paymentRepository.findById(authorizedPayment.getId()).orElseThrow();
        assertEquals(PaymentStatus.CAPTURED, reloadedPayment.getStatus());
        assertEquals(1, paymentTransactionRepository.findByPaymentIdOrderByOccurredAtAsc(authorizedPayment.getId()).size());

        List<WebhookEventRecord> events = webhookEventRepository.findAll();
        assertEquals(1, events.size());
        WebhookEventRecord event = events.get(0);
        assertEquals(WebhookEventStatus.PROCESSED, event.getStatus());
        assertTrue(event.getOutcomeReason() != null
                && event.getOutcomeReason().startsWith("payment captured, booking confirm skipped:"));

        Booking reloadedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertEquals(BookingState.CANCELLED, reloadedBooking.getState());
    }

    private String payloadJson(String eventId, String eventType, String providerReference, OffsetDateTime occurredAt) throws Exception {
        return objectMapper.writeValueAsString(new WebhookEventPayload(eventId, eventType, providerReference, occurredAt));
    }

    private PaymentOrder advanceToAuthorized(PaymentOrder payment) {
        payment.authorize();
        return paymentRepository.save(payment);
    }

    private PaymentOrder createPaymentFixture() {
        return createBookingAndPaymentFixture().payment();
    }

    private BookingPaymentFixture createBookingAndPaymentFixture() {
        IdentityAccount owner = createActiveAccount("owner-webhook-ctrl-" + UUID.randomUUID() + "@example.com");
        IdentityAccount renter = createActiveAccount("renter-webhook-ctrl-" + UUID.randomUUID() + "@example.com");

        Category category = categoryRepository.save(new Category("webhook-ctrl-cat-" + UUID.randomUUID(), "Webhook Ctrl Category"));

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

        return new BookingPaymentFixture(booking, savedPayment);
    }

    private IdentityAccount createActiveAccount(String email) {
        IdentityAccount account = identityAccountRepository.save(IdentityAccount.register(email, "hashed-password"));
        account.markEmailVerified();
        account.activate();
        return identityAccountRepository.save(account);
    }

    private record BookingPaymentFixture(Booking booking, PaymentOrder payment) {
    }
}
