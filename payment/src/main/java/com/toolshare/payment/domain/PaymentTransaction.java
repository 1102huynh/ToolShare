package com.toolshare.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "payment_transactions",
        indexes = {
                @Index(name = "idx_payment_transactions_payment_time", columnList = "payment_id,occurred_at")
        }
)
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private PaymentOrder payment;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private PaymentTransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status")
    private PaymentStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false)
    private PaymentStatus newStatus;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "provider_reference", length = 128)
    private String providerReference;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    protected PaymentTransaction() {
    }

    public PaymentTransaction(
            PaymentOrder payment,
            PaymentTransactionType transactionType,
            PaymentStatus previousStatus,
            PaymentStatus newStatus,
            long amountMinor,
            String providerReference
    ) {
        this.payment = Objects.requireNonNull(payment, "payment");
        this.transactionType = Objects.requireNonNull(transactionType, "transactionType");
        this.previousStatus = previousStatus;
        this.newStatus = Objects.requireNonNull(newStatus, "newStatus");
        this.amountMinor = amountMinor;
        this.providerReference = providerReference;
        this.occurredAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public PaymentOrder getPayment() {
        return payment;
    }

    public PaymentTransactionType getTransactionType() {
        return transactionType;
    }

    public PaymentStatus getPreviousStatus() {
        return previousStatus;
    }

    public PaymentStatus getNewStatus() {
        return newStatus;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }
}
