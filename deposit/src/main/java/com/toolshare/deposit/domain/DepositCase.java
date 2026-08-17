package com.toolshare.deposit.domain;

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

/**
 * Deposit aggregate root (T-013 plan §5). A separate aggregate and ledger from
 * rental payment (ARCHITECTURE §8, ADR-005) — deliberately structured to mirror
 * {@code payment.domain.PaymentOrder}'s shape (private constructor + static
 * factory, guarded wrapper transition methods delegating to a private
 * {@code transitionTo}), but with no provider integration (T-013 plan §7:
 * confirmed decision #2 — domain-only state transitions, no
 * {@code PaymentProvider}/{@code DepositProvider}).
 */
@Entity
@Table(
        name = "deposits",
        indexes = {
                @Index(name = "idx_deposits_status", columnList = "status")
        }
)
public class DepositCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "renter_id", nullable = false)
    private UUID renterId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "deducted_amount_minor", nullable = false)
    private long deductedAmountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DepositStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected DepositCase() {
    }

    private DepositCase(
            UUID bookingId,
            UUID renterId,
            long amountMinor,
            String currency
    ) {
        this.bookingId = Objects.requireNonNull(bookingId, "bookingId");
        this.renterId = Objects.requireNonNull(renterId, "renterId");
        this.amountMinor = requireMoneyAmount(amountMinor, "Amount must be zero or greater");
        this.currency = normalizeCurrency(currency);
        this.deductedAmountMinor = 0L;
        this.status = DepositStatus.REQUESTED;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Opens a new deposit case in {@code REQUESTED}. Named {@code open} (not
     * {@code initiate}, unlike {@code PaymentOrder}) to match
     * {@code DepositTransactionType.OPEN}'s ledger label for this row.
     */
    public static DepositCase open(
            UUID bookingId,
            UUID renterId,
            long amountMinor,
            String currency
    ) {
        return new DepositCase(bookingId, renterId, amountMinor, currency);
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

    public long getDeductedAmountMinor() {
        return deductedAmountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public DepositStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void authorize() {
        transitionTo(DepositStatus.AUTHORIZED);
    }

    public void hold() {
        transitionTo(DepositStatus.HELD);
    }

    /**
     * Targets {@code RELEASE_PENDING}. Reachable from either {@code HELD} or
     * {@code DISPUTED} (a resolved dispute with no deduction re-enters the release
     * path) — both are legal per {@link DepositStatus#canTransitionTo}, so this one
     * method serves both source states, same shape as {@link #release()} below.
     */
    public void scheduleRelease() {
        transitionTo(DepositStatus.RELEASE_PENDING);
    }

    /**
     * Targets {@code RELEASED}. Reachable from {@code RELEASE_PENDING} (full
     * release) or {@code PARTIALLY_DEDUCTED} (release of the undeducted remainder).
     */
    public void release() {
        transitionTo(DepositStatus.RELEASED);
    }

    /**
     * Targets {@code DISPUTED}. Reachable from {@code HELD} or
     * {@code RELEASE_PENDING} — the structural precondition for any deduction
     * (T-013 plan §6 confirmed decision #1).
     */
    public void dispute() {
        transitionTo(DepositStatus.DISPUTED);
    }

    /**
     * Applies a deduction of {@code amount} against the deposit. Only legal when
     * the current status is {@code DISPUTED} — deduction from {@code HELD} (or any
     * other status) is rejected by {@link DepositStatus#canTransitionTo} exactly
     * like every other illegal transition, no special-casing needed. Target status
     * is computed deterministically (integer arithmetic, no floating point) by
     * comparing the new cumulative deducted total against {@code amountMinor}:
     * equal ⇒ {@code FULLY_DEDUCTED}, otherwise {@code PARTIALLY_DEDUCTED}.
     *
     * @throws IllegalArgumentException if {@code amount} is not positive, or would
     *                                   push the cumulative deducted total above
     *                                   {@code amountMinor} — mirrors how
     *                                   {@code Booking.create}'s callers translate
     *                                   {@code IllegalArgumentException} into a
     *                                   validation-level rejection, distinct from
     *                                   the {@code IllegalStateException} thrown by
     *                                   {@link #transitionTo} for an illegal status.
     */
    public void deduct(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Deduction amount must be greater than zero");
        }
        long newDeducted = this.deductedAmountMinor + amount;
        if (newDeducted > this.amountMinor) {
            throw new IllegalArgumentException("Deduction exceeds remaining deposit amount");
        }
        DepositStatus target = newDeducted == this.amountMinor
                ? DepositStatus.FULLY_DEDUCTED
                : DepositStatus.PARTIALLY_DEDUCTED;
        transitionTo(target);
        this.deductedAmountMinor = newDeducted;
    }

    private void transitionTo(DepositStatus next) {
        Objects.requireNonNull(next, "next");
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("Deposit cannot transition from " + status + " to " + next);
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
