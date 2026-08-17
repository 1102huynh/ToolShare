package com.toolshare.deposit.domain;

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

/**
 * Append-only deposit ledger row (T-013 plan §8, BR-DEPOSIT-004 "immutable
 * deposit ledger"). One row per {@link DepositCase} state change, written in the
 * same {@code @Transactional} method as the mutation it records — never updated,
 * never deleted. Mirrors {@code payment.domain.PaymentTransaction} exactly.
 */
@Entity
@Table(
        name = "deposit_transactions",
        indexes = {
                @Index(name = "idx_deposit_transactions_deposit_time", columnList = "deposit_id,occurred_at")
        }
)
public class DepositTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deposit_id", nullable = false)
    private DepositCase deposit;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private DepositTransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status")
    private DepositStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false)
    private DepositStatus newStatus;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    protected DepositTransaction() {
    }

    public DepositTransaction(
            DepositCase deposit,
            DepositTransactionType transactionType,
            DepositStatus previousStatus,
            DepositStatus newStatus,
            long amountMinor,
            String reason
    ) {
        this.deposit = Objects.requireNonNull(deposit, "deposit");
        this.transactionType = Objects.requireNonNull(transactionType, "transactionType");
        this.previousStatus = previousStatus;
        this.newStatus = Objects.requireNonNull(newStatus, "newStatus");
        this.amountMinor = amountMinor;
        this.reason = reason;
        this.occurredAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public DepositCase getDeposit() {
        return deposit;
    }

    public DepositTransactionType getTransactionType() {
        return transactionType;
    }

    public DepositStatus getPreviousStatus() {
        return previousStatus;
    }

    public DepositStatus getNewStatus() {
        return newStatus;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }
}
