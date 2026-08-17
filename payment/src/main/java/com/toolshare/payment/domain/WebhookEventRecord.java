package com.toolshare.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Append-only ledger of received provider webhook events — a second, independent
 * ledger alongside {@link PaymentTransaction} (of received events, not of payment
 * states), matching the DOMAIN-MODEL.md §7 entity of the same name. One row is
 * written per event delivery attempt that reaches this far (i.e. after signature
 * verification and payment resolution have already succeeded — see
 * {@code PaymentWebhookService}), never before.
 */
@Entity
@Table(
        name = "webhook_events",
        indexes = {
                @Index(name = "idx_webhook_events_payment", columnList = "payment_id")
        }
)
public class WebhookEventRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "provider_name", nullable = false, length = 64)
    private String providerName;

    @Column(name = "provider_event_id", nullable = false, length = 128)
    private String providerEventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WebhookEventStatus status;

    @Column(name = "outcome_reason", length = 255)
    private String outcomeReason;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    protected WebhookEventRecord() {
    }

    public WebhookEventRecord(
            String providerName,
            String providerEventId,
            String eventType,
            UUID paymentId,
            WebhookEventStatus status,
            String outcomeReason,
            String payload
    ) {
        this.providerName = Objects.requireNonNull(providerName, "providerName");
        this.providerEventId = Objects.requireNonNull(providerEventId, "providerEventId");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.paymentId = Objects.requireNonNull(paymentId, "paymentId");
        this.status = Objects.requireNonNull(status, "status");
        this.outcomeReason = outcomeReason;
        this.payload = Objects.requireNonNull(payload, "payload");
        this.receivedAt = OffsetDateTime.now();
        // T-012's handler is fully synchronous, so the outcome is always known by the
        // time this row is written — processedAt is kept nullable at the schema level
        // only for forward-compatibility with a future async worker model (§10 of the plan).
        this.processedAt = this.receivedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public String getEventType() {
        return eventType;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public WebhookEventStatus getStatus() {
        return status;
    }

    public String getOutcomeReason() {
        return outcomeReason;
    }

    public String getPayload() {
        return payload;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }

    /**
     * Narrow, purpose-built mutator for exactly one case (T-012 plan §5): a
     * {@code CAPTURED} webhook event that itself applied successfully to
     * {@link PaymentOrder}, but whose {@code confirmAfterPayment} follow-up call
     * failed (e.g. the booking was independently cancelled mid-flight). The row's
     * {@code status} stays {@code PROCESSED} (the payment mutation did happen); only
     * the reason is annotated for manual reconciliation. Mirrors the narrow,
     * purpose-built-mutator precedent of {@code PaymentOrder.attachProviderReference}
     * rather than exposing a general setter.
     */
    public void recordBookingConfirmSkipped(String outcomeReason) {
        this.outcomeReason = Objects.requireNonNull(outcomeReason, "outcomeReason");
    }
}
