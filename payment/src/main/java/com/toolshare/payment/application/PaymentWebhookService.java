package com.toolshare.payment.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolshare.booking.application.BookingService;
import com.toolshare.common.exception.ConflictException;
import com.toolshare.common.exception.MalformedRequestException;
import com.toolshare.common.exception.ResourceNotFoundException;
import com.toolshare.common.exception.UnauthorizedException;
import com.toolshare.payment.api.dto.WebhookEventPayload;
import com.toolshare.payment.domain.PaymentOrder;
import com.toolshare.payment.domain.PaymentStatus;
import com.toolshare.payment.domain.PaymentTransaction;
import com.toolshare.payment.domain.PaymentTransactionType;
import com.toolshare.payment.domain.WebhookEventRecord;
import com.toolshare.payment.domain.WebhookEventStatus;
import com.toolshare.payment.infrastructure.persistence.PaymentRepository;
import com.toolshare.payment.infrastructure.persistence.PaymentTransactionRepository;
import com.toolshare.payment.infrastructure.persistence.WebhookEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Orchestrates inbound provider webhook events (T-012 plan §8): signature
 * verification, payload parsing, duplicate/idempotency handling, replay
 * protection, out-of-order classification, and — for the one specific
 * {@code AUTHORIZED -> CAPTURED} transition — booking confirmation (§5). One
 * {@code @Transactional} method, mirroring {@link PaymentService}'s all-or-nothing
 * shape.
 */
@Service
public class PaymentWebhookService {

    /**
     * {@code eventType} -> target {@link PaymentStatus} / recorded
     * {@link PaymentTransactionType}, per plan §9. All six values are pre-existing
     * {@link PaymentTransactionType} constants from T-011 — no enum changes.
     */
    private static final Map<String, EventMapping> EVENT_MAPPINGS = Map.of(
            "AUTHORIZED", new EventMapping(PaymentStatus.AUTHORIZED, PaymentTransactionType.AUTHORIZE),
            "CAPTURED", new EventMapping(PaymentStatus.CAPTURED, PaymentTransactionType.CAPTURE),
            "FAILED", new EventMapping(PaymentStatus.FAILED, PaymentTransactionType.FAIL),
            "CANCELLED", new EventMapping(PaymentStatus.CANCELLED, PaymentTransactionType.CANCEL),
            "REFUNDED", new EventMapping(PaymentStatus.REFUNDED, PaymentTransactionType.REFUND),
            "PARTIALLY_REFUNDED", new EventMapping(PaymentStatus.PARTIALLY_REFUNDED, PaymentTransactionType.REFUND)
    );

    /**
     * Ordinal position on the linear happy path, used only to distinguish
     * "stale/out-of-order" (current already advanced past target) from "missing
     * intermediate state" (target not yet reachable) per plan §9's decision table.
     * Side-branch terminal states (FAILED/CANCELLED/REFUNDED/PARTIALLY_REFUNDED) are
     * intentionally not part of this ordering.
     */
    private static final Map<PaymentStatus, Integer> HAPPY_PATH_ORDER = Map.of(
            PaymentStatus.INITIATED, 0,
            PaymentStatus.AUTHORIZED, 1,
            PaymentStatus.CAPTURED, 2,
            PaymentStatus.SETTLED, 3
    );

    private static final Set<PaymentStatus> TERMINAL_STATUSES = Set.of(
            PaymentStatus.SETTLED,
            PaymentStatus.FAILED,
            PaymentStatus.CANCELLED,
            PaymentStatus.REFUNDED,
            PaymentStatus.PARTIALLY_REFUNDED
    );

    private final WebhookSignatureVerifier signatureVerifier;
    private final WebhookSecurityProperties securityProperties;
    private final WebhookEventRepository webhookEventRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final BookingService bookingService;
    private final ObjectMapper objectMapper;

    public PaymentWebhookService(
            WebhookSignatureVerifier signatureVerifier,
            WebhookSecurityProperties securityProperties,
            WebhookEventRepository webhookEventRepository,
            PaymentRepository paymentRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            BookingService bookingService,
            ObjectMapper objectMapper
    ) {
        this.signatureVerifier = signatureVerifier;
        this.securityProperties = securityProperties;
        this.webhookEventRepository = webhookEventRepository;
        this.paymentRepository = paymentRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.bookingService = bookingService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WebhookEventRecord processWebhook(String providerName, String rawBody, String signatureHeader) {
        Objects.requireNonNull(providerName, "providerName");

        // Step 1 (§8): verify signature FIRST — before parsing, before any DB read.
        // An invalid signature must never result in a webhook_events row.
        if (!signatureVerifier.verify(providerName, rawBody, signatureHeader)) {
            throw new UnauthorizedException("Invalid webhook signature");
        }

        // Step 2: parse only after the signature is confirmed valid.
        WebhookEventPayload payload = parsePayload(rawBody);

        // Step 3: fast-path duplicate pre-check — optimization only, not the
        // authoritative guarantee (that's the DataIntegrityViolationException catch below).
        Optional<WebhookEventRecord> existing =
                webhookEventRepository.findByProviderNameAndProviderEventId(providerName, payload.eventId());
        if (existing.isPresent()) {
            return existing.get();
        }

        // Step 4: resolve the target payment. Not found -> 404, no row written —
        // a legitimate ordering race (webhook arrives before payment creation is
        // visible) can be retried safely later since nothing has been claimed yet.
        PaymentOrder payment = paymentRepository.findByProviderReference(payload.providerReference())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for provider reference"));

        // Steps 5-6: classify the outcome — pure, in-memory, no writes yet.
        WebhookOutcome outcome = classify(payment, payload);

        // Step 7: persist the WebhookEventRecord FIRST, before touching PaymentOrder.
        // This claims the (provider_name, provider_event_id) uniqueness slot atomically
        // against a genuinely concurrent duplicate delivery.
        WebhookEventRecord record = new WebhookEventRecord(
                providerName,
                payload.eventId(),
                payload.eventType(),
                payment.getId(),
                outcome.status(),
                outcome.reason(),
                rawBody
        );
        WebhookEventRecord saved;
        try {
            saved = webhookEventRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException ex) {
            // A concurrent duplicate delivery won the race — return its recorded
            // outcome, apply no mutation. This is BR-PAYMENT-001's real guarantee;
            // step 3's pre-check above is best-effort only.
            return webhookEventRepository.findByProviderNameAndProviderEventId(providerName, payload.eventId())
                    .orElseThrow(() -> ex);
        }

        // Step 8: apply the guarded PaymentOrder transition only for a PROCESSED outcome.
        if (outcome.status() == WebhookEventStatus.PROCESSED) {
            applyPaymentTransition(payment, outcome);

            // §5: narrow booking-confirmation wiring — only on AUTHORIZED -> CAPTURED.
            if (outcome.targetStatus() == PaymentStatus.CAPTURED) {
                confirmBookingAfterCapture(payment, saved);
            }
        }

        // Step 9: every case except signature failure (401), malformed payload (400),
        // and unresolved payment reference (404) returns normally (200 at the controller).
        return saved;
    }

    private WebhookEventPayload parsePayload(String rawBody) {
        WebhookEventPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, WebhookEventPayload.class);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new MalformedRequestException("Malformed webhook payload");
        }
        if (payload == null
                || isBlank(payload.eventId())
                || isBlank(payload.eventType())
                || isBlank(payload.providerReference())
                || payload.occurredAt() == null) {
            throw new MalformedRequestException("Malformed webhook payload");
        }
        return payload;
    }

    private WebhookOutcome classify(PaymentOrder payment, WebhookEventPayload payload) {
        // Step 5: replay/staleness check, evaluated independently of the eventId dedup
        // table — a replayed request might carry a never-before-seen eventId.
        OffsetDateTime cutoff = OffsetDateTime.now().minus(securityProperties.getReplayTolerance());
        if (payload.occurredAt().isBefore(cutoff)) {
            return WebhookOutcome.ignored("stale event outside replay tolerance");
        }

        EventMapping mapping = EVENT_MAPPINGS.get(payload.eventType());
        if (mapping == null) {
            return WebhookOutcome.failed("unsupported event type");
        }

        PaymentStatus current = payment.getStatus();
        PaymentStatus target = mapping.targetStatus();

        if (current == target) {
            return WebhookOutcome.ignored("already in target state, no-op");
        }
        if (current.canTransitionTo(target)) {
            return WebhookOutcome.processed(mapping.transactionType(), target);
        }

        Integer currentOrder = HAPPY_PATH_ORDER.get(current);
        Integer targetOrder = HAPPY_PATH_ORDER.get(target);
        if (currentOrder != null && targetOrder != null && currentOrder > targetOrder) {
            return WebhookOutcome.ignored("stale/out-of-order event, payment already advanced past this state");
        }
        if (TERMINAL_STATUSES.contains(current)) {
            return WebhookOutcome.failed("unexpected transition from terminal state");
        }
        return WebhookOutcome.failed("unexpected transition, missing intermediate state");
    }

    private void applyPaymentTransition(PaymentOrder payment, WebhookOutcome outcome) {
        PaymentStatus previousStatus = payment.getStatus();
        try {
            switch (outcome.transactionType()) {
                case AUTHORIZE -> payment.authorize();
                case CAPTURE -> payment.capture();
                case FAIL -> payment.fail();
                case CANCEL -> payment.cancel();
                case REFUND -> {
                    if (outcome.targetStatus() == PaymentStatus.PARTIALLY_REFUNDED) {
                        payment.partiallyRefund();
                    } else {
                        payment.refund();
                    }
                }
                case CREATE -> throw new IllegalStateException("CREATE is not a webhook-driven transition");
            }
        } catch (IllegalStateException ex) {
            throw new ConflictException(ex.getMessage());
        }

        PaymentOrder savedPayment = paymentRepository.save(payment);
        paymentTransactionRepository.save(new PaymentTransaction(
                savedPayment,
                outcome.transactionType(),
                previousStatus,
                savedPayment.getStatus(),
                savedPayment.getAmountMinor(),
                savedPayment.getProviderReference()
        ));
    }

    /**
     * §5: the only trigger point into {@code booking}. A failure here (e.g. the
     * booking was independently cancelled mid-flight) must not fail the webhook
     * response — the payment has already correctly moved to CAPTURED — so it is
     * caught and annotated on the already-persisted {@link WebhookEventRecord} for
     * manual reconciliation instead of propagating.
     */
    private void confirmBookingAfterCapture(PaymentOrder payment, WebhookEventRecord record) {
        try {
            bookingService.confirmAfterPayment(payment.getBookingId());
        } catch (RuntimeException ex) {
            record.recordBookingConfirmSkipped("payment captured, booking confirm skipped: " + ex.getMessage());
            webhookEventRepository.save(record);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record EventMapping(PaymentStatus targetStatus, PaymentTransactionType transactionType) {
    }

    private record WebhookOutcome(
            WebhookEventStatus status,
            String reason,
            PaymentTransactionType transactionType,
            PaymentStatus targetStatus
    ) {
        static WebhookOutcome processed(PaymentTransactionType transactionType, PaymentStatus targetStatus) {
            return new WebhookOutcome(WebhookEventStatus.PROCESSED, null, transactionType, targetStatus);
        }

        static WebhookOutcome ignored(String reason) {
            return new WebhookOutcome(WebhookEventStatus.IGNORED, reason, null, null);
        }

        static WebhookOutcome failed(String reason) {
            return new WebhookOutcome(WebhookEventStatus.FAILED, reason, null, null);
        }
    }
}
