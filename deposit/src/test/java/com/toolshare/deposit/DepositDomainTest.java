package com.toolshare.deposit;

import com.toolshare.deposit.domain.DepositCase;
import com.toolshare.deposit.domain.DepositStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pure unit test (no Spring), mirroring {@code PaymentDomainTest}'s style. Pins
 * every allowed edge and every rejected edge of {@link DepositStatus#canTransitionTo}
 * (T-013 plan §6/§16) — in particular that deduction is reachable ONLY from
 * {@code DISPUTED}, never directly from {@code HELD} (confirmed decision #1).
 */
class DepositDomainTest {

    // --- Construction -------------------------------------------------

    @Test
    void opened_deposit_starts_in_requested_state_with_zero_deducted() {
        DepositCase deposit = createDeposit(500_000L);

        assertEquals(DepositStatus.REQUESTED, deposit.getStatus());
        assertEquals(0L, deposit.getDeductedAmountMinor());
        assertEquals(500_000L, deposit.getAmountMinor());
    }

    @Test
    void zero_amount_deposit_is_allowed() {
        DepositCase deposit = createDeposit(0L);

        assertEquals(0L, deposit.getAmountMinor());
        assertEquals(DepositStatus.REQUESTED, deposit.getStatus());
    }

    @Test
    void negative_amount_is_rejected() {
        assertThrows(IllegalArgumentException.class, () -> DepositCase.open(
                UUID.randomUUID(), UUID.randomUUID(), -1L, "VND"
        ));
    }

    @Test
    void malformed_currency_is_rejected() {
        assertThrows(IllegalArgumentException.class, () -> DepositCase.open(
                UUID.randomUUID(), UUID.randomUUID(), 500_000L, "VNDX"
        ));
    }

    // --- Happy path: REQUESTED -> AUTHORIZED -> HELD -------------------

    @Test
    void requested_deposit_can_reach_held_through_authorize_and_hold() {
        DepositCase deposit = createDeposit(500_000L);

        deposit.authorize();
        assertEquals(DepositStatus.AUTHORIZED, deposit.getStatus());

        deposit.hold();
        assertEquals(DepositStatus.HELD, deposit.getStatus());
    }

    // --- Release path without dispute ----------------------------------

    @Test
    void held_deposit_can_schedule_release_and_release_in_full() {
        DepositCase deposit = heldDeposit(500_000L);

        deposit.scheduleRelease();
        assertEquals(DepositStatus.RELEASE_PENDING, deposit.getStatus());

        deposit.release();
        assertEquals(DepositStatus.RELEASED, deposit.getStatus());
        assertEquals(0L, deposit.getDeductedAmountMinor());
    }

    // --- Dispute path ----------------------------------------------------

    @Test
    void held_deposit_can_be_disputed() {
        DepositCase deposit = heldDeposit(500_000L);

        deposit.dispute();

        assertEquals(DepositStatus.DISPUTED, deposit.getStatus());
    }

    @Test
    void release_pending_deposit_can_be_disputed() {
        DepositCase deposit = heldDeposit(500_000L);
        deposit.scheduleRelease();

        deposit.dispute();

        assertEquals(DepositStatus.DISPUTED, deposit.getStatus());
    }

    @Test
    void disputed_deposit_can_resolve_to_release_pending_without_deduction() {
        DepositCase deposit = disputedDeposit(500_000L);

        deposit.scheduleRelease();

        assertEquals(DepositStatus.RELEASE_PENDING, deposit.getStatus());
        assertEquals(0L, deposit.getDeductedAmountMinor());
    }

    // --- Deduction: legal only from DISPUTED ----------------------------

    @Test
    void disputed_deposit_partial_deduction_transitions_to_partially_deducted() {
        DepositCase deposit = disputedDeposit(500_000L);

        deposit.deduct(200_000L);

        assertEquals(DepositStatus.PARTIALLY_DEDUCTED, deposit.getStatus());
        assertEquals(200_000L, deposit.getDeductedAmountMinor());
    }

    @Test
    void disputed_deposit_full_deduction_transitions_to_fully_deducted() {
        DepositCase deposit = disputedDeposit(500_000L);

        deposit.deduct(500_000L);

        assertEquals(DepositStatus.FULLY_DEDUCTED, deposit.getStatus());
        assertEquals(500_000L, deposit.getDeductedAmountMinor());
    }

    @Test
    void partially_deducted_deposit_can_release_the_remainder() {
        DepositCase deposit = disputedDeposit(500_000L);
        deposit.deduct(200_000L);

        deposit.release();

        assertEquals(DepositStatus.RELEASED, deposit.getStatus());
        assertEquals(200_000L, deposit.getDeductedAmountMinor());
    }

    /**
     * Explicit, named regression per the review-confirmed scope: deduction from
     * {@code HELD} must be rejected — there is no {@code HELD -> *_DEDUCTED} edge.
     * Deduction is reachable ONLY via {@code DISPUTED} (confirmed decision #1).
     */
    @Test
    void deduction_from_held_is_rejected() {
        DepositCase deposit = heldDeposit(500_000L);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> deposit.deduct(100_000L));

        assertEquals("Deposit cannot transition from HELD to PARTIALLY_DEDUCTED", exception.getMessage());
        assertEquals(DepositStatus.HELD, deposit.getStatus());
        assertEquals(0L, deposit.getDeductedAmountMinor());
    }

    /**
     * Same rejection, but for the full-deduction target specifically — confirms
     * the guard applies regardless of which deduction outcome would result.
     */
    @Test
    void full_deduction_from_held_is_also_rejected() {
        DepositCase deposit = heldDeposit(500_000L);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> deposit.deduct(500_000L));

        assertEquals("Deposit cannot transition from HELD to FULLY_DEDUCTED", exception.getMessage());
        assertEquals(DepositStatus.HELD, deposit.getStatus());
        assertEquals(0L, deposit.getDeductedAmountMinor());
    }

    @Test
    void deduction_from_release_pending_is_rejected() {
        DepositCase deposit = heldDeposit(500_000L);
        deposit.scheduleRelease();

        assertThrows(IllegalStateException.class, () -> deposit.deduct(100_000L));
        assertEquals(DepositStatus.RELEASE_PENDING, deposit.getStatus());
    }

    @Test
    void deduction_from_requested_is_rejected() {
        DepositCase deposit = createDeposit(500_000L);

        assertThrows(IllegalStateException.class, () -> deposit.deduct(100_000L));
        assertEquals(DepositStatus.REQUESTED, deposit.getStatus());
    }

    /**
     * A second deduction cycle is out of T-013's method scope (plan §6): once
     * {@code PARTIALLY_DEDUCTED}, the only legal edge is {@code -> RELEASED}
     * (release of the remainder) — {@code DISPUTED} is reachable only from
     * {@code HELD}/{@code RELEASE_PENDING}, not from {@code PARTIALLY_DEDUCTED},
     * so a second {@code deduct()} call is structurally unreachable, not merely
     * discouraged.
     */
    @Test
    void second_deduction_after_partial_deduction_is_rejected() {
        DepositCase deposit = disputedDeposit(500_000L);
        deposit.deduct(200_000L);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> deposit.deduct(100_000L));

        assertEquals("Deposit cannot transition from PARTIALLY_DEDUCTED to PARTIALLY_DEDUCTED", exception.getMessage());
        assertEquals(200_000L, deposit.getDeductedAmountMinor());
    }

    @Test
    void deduction_exceeding_remaining_amount_is_rejected() {
        DepositCase deposit = disputedDeposit(500_000L);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> deposit.deduct(500_001L));

        assertEquals("Deduction exceeds remaining deposit amount", exception.getMessage());
        assertEquals(DepositStatus.DISPUTED, deposit.getStatus());
        assertEquals(0L, deposit.getDeductedAmountMinor());
    }

    /**
     * {@code deduct}'s amount guard runs BEFORE its state guard (§ code order),
     * so when a second deduction request both violates the (structurally
     * unreachable, per {@link #second_deduction_after_partial_deduction_is_rejected()})
     * state precondition AND would exceed the remaining amount, the amount guard
     * is the one that actually fires — {@link IllegalArgumentException}, not
     * {@link IllegalStateException}. Pinned explicitly so this ordering is a
     * documented behavior, not an accident.
     */
    @Test
    void second_deduction_exceeding_remainder_is_rejected_by_the_amount_guard_first() {
        DepositCase deposit = disputedDeposit(500_000L);
        deposit.deduct(400_000L);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> deposit.deduct(200_000L));

        assertEquals("Deduction exceeds remaining deposit amount", exception.getMessage());
        assertEquals(400_000L, deposit.getDeductedAmountMinor());
    }

    @Test
    void zero_deduction_amount_is_rejected() {
        DepositCase deposit = disputedDeposit(500_000L);

        assertThrows(IllegalArgumentException.class, () -> deposit.deduct(0L));
        assertEquals(0L, deposit.getDeductedAmountMinor());
    }

    @Test
    void negative_deduction_amount_is_rejected() {
        DepositCase deposit = disputedDeposit(500_000L);

        assertThrows(IllegalArgumentException.class, () -> deposit.deduct(-1L));
        assertEquals(0L, deposit.getDeductedAmountMinor());
    }

    // --- Terminal states -------------------------------------------------

    @Test
    void released_deposit_rejects_further_transitions() {
        DepositCase deposit = heldDeposit(500_000L);
        deposit.scheduleRelease();
        deposit.release();

        IllegalStateException exception = assertThrows(IllegalStateException.class, deposit::scheduleRelease);

        assertEquals("Deposit cannot transition from RELEASED to RELEASE_PENDING", exception.getMessage());
    }

    @Test
    void fully_deducted_deposit_rejects_further_transitions() {
        DepositCase deposit = disputedDeposit(500_000L);
        deposit.deduct(500_000L);

        IllegalStateException exception = assertThrows(IllegalStateException.class, deposit::release);

        assertEquals("Deposit cannot transition from FULLY_DEDUCTED to RELEASED", exception.getMessage());
    }

    @Test
    void release_before_release_pending_is_rejected() {
        DepositCase deposit = heldDeposit(500_000L);

        IllegalStateException exception = assertThrows(IllegalStateException.class, deposit::release);

        assertEquals("Deposit cannot transition from HELD to RELEASED", exception.getMessage());
    }

    @Test
    void hold_before_authorize_is_rejected() {
        DepositCase deposit = createDeposit(500_000L);

        IllegalStateException exception = assertThrows(IllegalStateException.class, deposit::hold);

        assertEquals("Deposit cannot transition from REQUESTED to HELD", exception.getMessage());
    }

    // --- Fixtures ----------------------------------------------------------

    private DepositCase createDeposit(long amountMinor) {
        return DepositCase.open(UUID.randomUUID(), UUID.randomUUID(), amountMinor, "VND");
    }

    private DepositCase heldDeposit(long amountMinor) {
        DepositCase deposit = createDeposit(amountMinor);
        deposit.authorize();
        deposit.hold();
        return deposit;
    }

    private DepositCase disputedDeposit(long amountMinor) {
        DepositCase deposit = heldDeposit(amountMinor);
        deposit.dispute();
        return deposit;
    }
}
