package com.toolshare.deposit;

import com.toolshare.booking.domain.Booking;
import com.toolshare.booking.domain.BookingPolicy;
import com.toolshare.booking.infrastructure.persistence.BookingRepository;
import com.toolshare.common.exception.ConflictException;
import com.toolshare.common.exception.ForbiddenException;
import com.toolshare.common.exception.ValidationException;
import com.toolshare.deposit.application.DepositService;
import com.toolshare.deposit.domain.DepositCase;
import com.toolshare.deposit.domain.DepositStatus;
import com.toolshare.deposit.domain.DepositTransaction;
import com.toolshare.deposit.domain.DepositTransactionType;
import com.toolshare.deposit.infrastructure.persistence.DepositRepository;
import com.toolshare.deposit.infrastructure.persistence.DepositTransactionRepository;
import com.toolshare.identity.domain.IdentityAccount;
import com.toolshare.identity.infrastructure.persistence.IdentityAccountRepository;
import com.toolshare.listing.domain.Category;
import com.toolshare.listing.domain.ListingLocation;
import com.toolshare.listing.domain.ToolListing;
import com.toolshare.listing.infrastructure.persistence.CategoryRepository;
import com.toolshare.listing.infrastructure.persistence.ToolListingRepository;
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

/**
 * Mirrors {@code PaymentServiceIntegrationTest}'s fixture/assertion style
 * (T-013 plan §16). Covers hold/release/dispute/deduction transitions end to
 * end through {@link DepositService}, the two DOCUMENTED ROADMAP test bullets
 * (hold/release/deduction state transitions; immutable ledger behavior), and —
 * per confirmed scope — that deduction is reachable only via {@code DISPUTED},
 * never directly from {@code HELD}.
 */
@SpringBootTest(classes = DepositApplication.class)
@AutoConfigureTestDatabase
class DepositServiceIntegrationTest {

    @Autowired
    private DepositService depositService;

    @Autowired
    private DepositRepository depositRepository;

    @Autowired
    private DepositTransactionRepository depositTransactionRepository;

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
    void hold_deposit_reaches_held_with_three_ledger_rows_and_correct_amount() {
        Fixture fixture = createFixture(300_000L);
        Booking booking = createBooking(fixture);

        DepositCase deposit = depositService.holdDeposit(booking.getId(), fixture.renter.getId());

        assertEquals(DepositStatus.HELD, deposit.getStatus());
        assertEquals(300_000L, deposit.getAmountMinor());
        assertEquals(0L, deposit.getDeductedAmountMinor());
        assertEquals("VND", deposit.getCurrency());

        List<DepositTransaction> ledger = depositTransactionRepository.findByDepositIdOrderByOccurredAtAsc(deposit.getId());
        assertEquals(3, ledger.size());
        assertEquals(DepositTransactionType.OPEN, ledger.get(0).getTransactionType());
        assertEquals(DepositTransactionType.AUTHORIZE, ledger.get(1).getTransactionType());
        assertEquals(DepositTransactionType.HOLD, ledger.get(2).getTransactionType());
    }

    @Test
    @Transactional
    void non_renter_actor_cannot_hold_deposit() {
        Fixture fixture = createFixture(300_000L);
        Booking booking = createBooking(fixture);

        assertThrows(ForbiddenException.class, () -> depositService.holdDeposit(booking.getId(), fixture.owner.getId()));
    }

    @Test
    @Transactional
    void duplicate_hold_deposit_for_same_booking_is_rejected() {
        Fixture fixture = createFixture(300_000L);
        Booking booking = createBooking(fixture);
        depositService.holdDeposit(booking.getId(), fixture.renter.getId());

        assertThrows(ConflictException.class, () -> depositService.holdDeposit(booking.getId(), fixture.renter.getId()));
    }

    @Test
    @Transactional
    void zero_deposit_amount_listing_produces_zero_amount_deposit() {
        Fixture fixture = createFixture(0L);
        Booking booking = createBooking(fixture);

        DepositCase deposit = depositService.holdDeposit(booking.getId(), fixture.renter.getId());

        assertEquals(0L, deposit.getAmountMinor());
        assertEquals(DepositStatus.HELD, deposit.getStatus());
    }

    @Test
    @Transactional
    void release_without_dispute_reaches_released_with_no_deduction() {
        DepositCase held = holdFixtureDeposit(300_000L);

        DepositCase scheduled = depositService.scheduleRelease(held.getId());
        assertEquals(DepositStatus.RELEASE_PENDING, scheduled.getStatus());

        DepositCase released = depositService.releaseDeposit(held.getId());
        assertEquals(DepositStatus.RELEASED, released.getStatus());
        assertEquals(0L, released.getDeductedAmountMinor());

        List<DepositTransaction> ledger = depositTransactionRepository.findByDepositIdOrderByOccurredAtAsc(held.getId());
        assertEquals(5, ledger.size());
        DepositTransaction releaseRow = ledger.get(4);
        assertEquals(DepositTransactionType.RELEASE, releaseRow.getTransactionType());
        assertEquals(300_000L, releaseRow.getAmountMinor());
    }

    @Test
    @Transactional
    void dispute_then_partial_deduction_then_release_of_remainder() {
        DepositCase held = holdFixtureDeposit(500_000L);

        DepositCase disputed = depositService.disputeDeposit(held.getId(), "damage claim opened");
        assertEquals(DepositStatus.DISPUTED, disputed.getStatus());

        DepositCase deducted = depositService.deduct(held.getId(), 200_000L, "repair cost");
        assertEquals(DepositStatus.PARTIALLY_DEDUCTED, deducted.getStatus());
        assertEquals(200_000L, deducted.getDeductedAmountMinor());
        assertEquals(500_000L, deducted.getAmountMinor());

        DepositCase released = depositService.releaseDeposit(held.getId());
        assertEquals(DepositStatus.RELEASED, released.getStatus());
        assertEquals(200_000L, released.getDeductedAmountMinor());

        List<DepositTransaction> ledger = depositTransactionRepository.findByDepositIdOrderByOccurredAtAsc(held.getId());
        // OPEN, AUTHORIZE, HOLD, DISPUTE, DEDUCT, RELEASE
        assertEquals(6, ledger.size());
        DepositTransaction deductRow = ledger.get(4);
        assertEquals(DepositTransactionType.DEDUCT, deductRow.getTransactionType());
        assertEquals(200_000L, deductRow.getAmountMinor());
        assertEquals("repair cost", deductRow.getReason());
        DepositTransaction releaseRow = ledger.get(5);
        assertEquals(300_000L, releaseRow.getAmountMinor());
    }

    @Test
    @Transactional
    void dispute_then_full_deduction_reaches_fully_deducted_terminal() {
        DepositCase held = holdFixtureDeposit(400_000L);

        depositService.disputeDeposit(held.getId(), "total loss");
        DepositCase deducted = depositService.deduct(held.getId(), 400_000L, "replacement cost");

        assertEquals(DepositStatus.FULLY_DEDUCTED, deducted.getStatus());
        assertEquals(400_000L, deducted.getDeductedAmountMinor());

        assertThrows(ConflictException.class, () -> depositService.releaseDeposit(held.getId()));
    }

    /**
     * DOCUMENTED per the confirmed scope: deduction must be rejected when the
     * deposit is {@code HELD} (no dispute opened yet) — deduction is reachable
     * ONLY via {@code DISPUTED}.
     */
    @Test
    @Transactional
    void deduction_from_held_is_rejected_and_state_is_unchanged() {
        DepositCase held = holdFixtureDeposit(300_000L);

        assertThrows(ConflictException.class, () -> depositService.deduct(held.getId(), 100_000L, "no dispute yet"));

        DepositCase reloaded = depositService.getDeposit(held.getId());
        assertEquals(DepositStatus.HELD, reloaded.getStatus());
        assertEquals(0L, reloaded.getDeductedAmountMinor());
        assertEquals(3, depositTransactionRepository.findByDepositIdOrderByOccurredAtAsc(held.getId()).size());
    }

    @Test
    @Transactional
    void deduction_from_release_pending_is_rejected() {
        DepositCase held = holdFixtureDeposit(300_000L);
        depositService.scheduleRelease(held.getId());

        assertThrows(ConflictException.class, () -> depositService.deduct(held.getId(), 100_000L, "still no dispute"));

        assertEquals(DepositStatus.RELEASE_PENDING, depositService.getDeposit(held.getId()).getStatus());
    }

    @Test
    @Transactional
    void deduction_exceeding_amount_is_rejected_and_state_unchanged() {
        DepositCase held = holdFixtureDeposit(300_000L);
        depositService.disputeDeposit(held.getId(), "damage");

        assertThrows(ValidationException.class, () -> depositService.deduct(held.getId(), 300_001L, "too much"));

        DepositCase reloaded = depositService.getDeposit(held.getId());
        assertEquals(DepositStatus.DISPUTED, reloaded.getStatus());
        assertEquals(0L, reloaded.getDeductedAmountMinor());
    }

    /**
     * Confirms the {@code deducted_amount_minor <= amount_minor} DB invariant
     * matches the domain guard exactly: the domain rejects first (via
     * {@link ValidationException}), so the DB CHECK constraint is never actually
     * exercised through the service in ordinary use — it is defense-in-depth,
     * verified separately not to be the only thing standing between an
     * over-deduction and a persisted row.
     */
    @Test
    @Transactional
    void deposit_amount_and_deducted_amount_stay_consistent_after_partial_deduction() {
        DepositCase held = holdFixtureDeposit(500_000L);
        depositService.disputeDeposit(held.getId(), "damage");

        DepositCase deducted = depositService.deduct(held.getId(), 150_000L, "partial repair");

        assertEquals(500_000L, deducted.getAmountMinor());
        assertEquals(150_000L, deducted.getDeductedAmountMinor());
        DepositCase reloaded = depositRepository.findById(held.getId()).orElseThrow();
        assertEquals(500_000L, reloaded.getAmountMinor());
        assertEquals(150_000L, reloaded.getDeductedAmountMinor());
    }

    @Test
    @Transactional
    void immutable_ledger_rows_are_never_updated_only_appended() {
        DepositCase held = holdFixtureDeposit(300_000L);
        List<DepositTransaction> before = depositTransactionRepository.findByDepositIdOrderByOccurredAtAsc(held.getId());
        List<UUID> beforeIds = before.stream().map(DepositTransaction::getId).toList();

        depositService.scheduleRelease(held.getId());
        depositService.releaseDeposit(held.getId());

        List<DepositTransaction> after = depositTransactionRepository.findByDepositIdOrderByOccurredAtAsc(held.getId());
        assertEquals(5, after.size());
        for (int i = 0; i < beforeIds.size(); i++) {
            assertEquals(beforeIds.get(i), after.get(i).getId());
        }
    }

    @Test
    @Transactional
    void each_transition_appends_exactly_one_ledger_row() {
        Fixture fixture = createFixture(300_000L);
        Booking booking = createBooking(fixture);

        DepositCase held = depositService.holdDeposit(booking.getId(), fixture.renter.getId());
        assertEquals(3, depositTransactionRepository.findByDepositIdOrderByOccurredAtAsc(held.getId()).size());

        depositService.disputeDeposit(held.getId(), "dispute");
        assertEquals(4, depositTransactionRepository.findByDepositIdOrderByOccurredAtAsc(held.getId()).size());

        depositService.deduct(held.getId(), 100_000L, "partial");
        assertEquals(5, depositTransactionRepository.findByDepositIdOrderByOccurredAtAsc(held.getId()).size());

        depositService.releaseDeposit(held.getId());
        assertEquals(6, depositTransactionRepository.findByDepositIdOrderByOccurredAtAsc(held.getId()).size());
    }

    @Test
    @Transactional
    void get_deposit_by_booking_returns_the_associated_deposit() {
        Fixture fixture = createFixture(300_000L);
        Booking booking = createBooking(fixture);
        DepositCase created = depositService.holdDeposit(booking.getId(), fixture.renter.getId());

        DepositCase found = depositService.getDepositByBooking(booking.getId());

        assertEquals(created.getId(), found.getId());
    }

    private DepositCase holdFixtureDeposit(long depositAmountMinor) {
        Fixture fixture = createFixture(depositAmountMinor);
        Booking booking = createBooking(fixture);
        return depositService.holdDeposit(booking.getId(), fixture.renter.getId());
    }

    private Booking createBooking(Fixture fixture) {
        return bookingRepository.save(Booking.create(
                fixture.listing.getId(),
                fixture.renter.getId(),
                fixture.owner.getId(),
                fixture.start,
                fixture.start.plusDays(2),
                BookingPolicy.INSTANT
        ));
    }

    private Fixture createFixture(long depositAmountMinor) {
        IdentityAccount owner = createActiveAccount("owner-deposit-" + UUID.randomUUID() + "@example.com");
        IdentityAccount renter = createActiveAccount("renter-deposit-" + UUID.randomUUID() + "@example.com");

        Category category = categoryRepository.save(new Category("deposit-cat-" + UUID.randomUUID(), "Deposit Category"));

        ToolListing listing = new ToolListing(
                owner.getId(),
                category,
                new ListingLocation("1 Le Duan", null, "Ben Nghe", "District 1", "Ho Chi Minh City", "VN"),
                "Impact Driver",
                "Cordless impact driver",
                200_000L,
                depositAmountMinor,
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
