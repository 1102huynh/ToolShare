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
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "payments",
        indexes = {
                @Index(name = "idx_payments_status", columnList = "status")
        }
)
public class PaymentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "renter_id", nullable = false)
    private UUID renterId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "provider_name", nullable = false, length = 64)
    private String providerName;

    @Column(name = "provider_reference", length = 128)
    private String providerReference;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PaymentOrder() {
    }

    private PaymentOrder(
            UUID bookingId,
            UUID renterId,
            long amountMinor,
            String currency,
            String providerName
    ) {
        this.bookingId = Objects.requireNonNull(bookingId, "bookingId");
        this.renterId = Objects.requireNonNull(renterId, "renterId");
        this.amountMinor = requireMoneyAmount(amountMinor, "Amount must be zero or greater");
        this.currency = normalizeCurrency(currency);
        this.providerName = requireText(providerName, "Provider name is required");
        this.status = PaymentStatus.INITIATED;
        this.providerReference = null;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static PaymentOrder initiate(
            UUID bookingId,
            UUID renterId,
            long amountMinor,
            String currency,
            String providerName
    ) {
        return new PaymentOrder(bookingId, renterId, amountMinor, currency, providerName);
    }

    public UUID getId() {
        return id;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public UUID getRenterId() {
        return renterId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void attachProviderReference(String providerReference) {
        this.providerReference = Objects.requireNonNull(providerReference, "providerReference");
        this.updatedAt = OffsetDateTime.now();
    }

    public void authorize() {
        transitionTo(PaymentStatus.AUTHORIZED);
    }

    public void capture() {
        transitionTo(PaymentStatus.CAPTURED);
    }

    public void fail() {
        transitionTo(PaymentStatus.FAILED);
    }

    public void cancel() {
        transitionTo(PaymentStatus.CANCELLED);
    }

    private void transitionTo(PaymentStatus next) {
        Objects.requireNonNull(next, "next");
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("Payment cannot transition from " + status + " to " + next);
        }
        this.status = next;
        this.updatedAt = OffsetDateTime.now();
    }

    private static long requireMoneyAmount(long value, String message) {
        if (value < 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static String requireText(String value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String normalizeCurrency(String value) {
        String normalized = requireText(value, "Currency is required").toUpperCase(Locale.ROOT);
        if (normalized.length() != 3) {
            throw new IllegalArgumentException("Currency must be a 3-letter code");
        }
        return normalized;
    }
}
