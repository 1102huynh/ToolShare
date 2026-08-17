package com.toolshare.deposit.application;

import com.toolshare.booking.domain.Booking;
import com.toolshare.booking.infrastructure.persistence.BookingRepository;
import com.toolshare.common.exception.ConflictException;
import com.toolshare.common.exception.ForbiddenException;
import com.toolshare.common.exception.ResourceNotFoundException;
import com.toolshare.common.exception.ValidationException;
import com.toolshare.deposit.domain.DepositCase;
import com.toolshare.deposit.domain.DepositStatus;
import com.toolshare.deposit.domain.DepositTransaction;
import com.toolshare.deposit.domain.DepositTransactionType;
import com.toolshare.deposit.infrastructure.persistence.DepositRepository;
import com.toolshare.deposit.infrastructure.persistence.DepositTransactionRepository;
import com.toolshare.identity.domain.IdentityAccount;
import com.toolshare.identity.infrastructure.persistence.IdentityAccountRepository;
import com.toolshare.listing.domain.ToolListing;
import com.toolshare.listing.infrastructure.persistence.ToolListingRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * Deposit aggregate's application service (T-013 plan §10/§12), mirroring
 * {@code payment.application.PaymentService}'s layering exactly: cross-module
 * aggregates ({@link Booking}, {@link ToolListing}, {@link IdentityAccount}) are
 * read-only via their own Spring Data repositories, never written
 * (ARCHITECTURE §3 "Payment and deposit do not directly mutate booking state").
 * No provider port is used or needed (T-013 plan §7, confirmed decision #2).
 */
@Service
public class DepositService {

    private final DepositRepository depositRepository;
    private final DepositTransactionRepository depositTransactionRepository;
    private final BookingRepository bookingRepository;
    private final ToolListingRepository toolListingRepository;
    private final IdentityAccountRepository identityAccountRepository;

    public DepositService(
            DepositRepository depositRepository,
            DepositTransactionRepository depositTransactionRepository,
            BookingRepository bookingRepository,
            ToolListingRepository toolListingRepository,
            IdentityAccountRepository identityAccountRepository
    ) {
        this.depositRepository = depositRepository;
        this.depositTransactionRepository = depositTransactionRepository;
        this.bookingRepository = bookingRepository;
        this.toolListingRepository = toolListingRepository;
        this.identityAccountRepository = identityAccountRepository;
    }

    /**
     * Opens a deposit for {@code bookingId} (amount/currency read from the
     * booking's {@link ToolListing#getDepositAmount()}/{@link ToolListing#getCurrency()},
     * same MVP-interim, no-snapshot posture T-011 used for the rental amount) and
     * drives it {@code REQUESTED -> AUTHORIZED -> HELD} in one transaction, writing
     * the three corresponding ledger rows (OPEN/AUTHORIZE/HOLD). This is an
     * explicit command only — nothing in T-013 calls it automatically from booking
     * confirmation (T-013 plan §11, confirmed decision #3).
     */
    @Transactional
    public DepositCase holdDeposit(UUID bookingId, UUID actorAccountId) {
        Booking booking = requireBooking(bookingId);
        requireRenterActor(booking, actorAccountId);

        if (depositRepository.findByBookingId(booking.getId()).isPresent()) {
            throw new ConflictException("Deposit already exists for this booking");
        }

        ToolListing listing = toolListingRepository.findById(booking.getListingId())
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));

        long amountMinor = listing.getDepositAmount();
        String currency = listing.getCurrency();

        DepositCase deposit;
        try {
            deposit = depositRepository.saveAndFlush(DepositCase.open(
                    booking.getId(),
                    booking.getRenterId(),
                    amountMinor,
                    currency
            ));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Deposit already exists for this booking");
        }
        depositTransactionRepository.save(new DepositTransaction(
                deposit, DepositTransactionType.OPEN, null, deposit.getStatus(), amountMinor, null
        ));

        DepositStatus afterOpen = deposit.getStatus();
        applyTransition(deposit::authorize);
        DepositCase authorized = depositRepository.save(deposit);
        depositTransactionRepository.save(new DepositTransaction(
                authorized, DepositTransactionType.AUTHORIZE, afterOpen, authorized.getStatus(), amountMinor, null
        ));

        DepositStatus afterAuthorize = authorized.getStatus();
        applyTransition(deposit::hold);
        DepositCase held = depositRepository.save(deposit);
        depositTransactionRepository.save(new DepositTransaction(
                held, DepositTransactionType.HOLD, afterAuthorize, held.getStatus(), amountMinor, null
        ));

        return held;
    }

    @Transactional
    public DepositCase scheduleRelease(UUID depositId) {
        DepositCase deposit = requireDeposit(depositId);
        DepositStatus previousStatus = deposit.getStatus();
        applyTransition(deposit::scheduleRelease);
        return persistTransition(deposit, DepositTransactionType.SCHEDULE_RELEASE, previousStatus, deposit.getAmountMinor(), null);
    }

    /**
     * Releases the deposit: {@code RELEASE_PENDING -> RELEASED} (full release) or
     * {@code PARTIALLY_DEDUCTED -> RELEASED} (release of the undeducted
     * remainder). The ledger row's amount is the remainder actually released
     * ({@code amountMinor - deductedAmountMinor}), not the original deposit amount.
     */
    @Transactional
    public DepositCase releaseDeposit(UUID depositId) {
        DepositCase deposit = requireDeposit(depositId);
        DepositStatus previousStatus = deposit.getStatus();
        long remaining = deposit.getAmountMinor() - deposit.getDeductedAmountMinor();
        applyTransition(deposit::release);
        return persistTransition(deposit, DepositTransactionType.RELEASE, previousStatus, remaining, null);
    }

    @Transactional
    public DepositCase disputeDeposit(UUID depositId, String reason) {
        DepositCase deposit = requireDeposit(depositId);
        DepositStatus previousStatus = deposit.getStatus();
        applyTransition(deposit::dispute);
        return persistTransition(deposit, DepositTransactionType.DISPUTE, previousStatus, deposit.getAmountMinor(), normalizeReason(reason));
    }

    /**
     * Applies a deduction. Only legal when the deposit is currently
     * {@code DISPUTED} (T-013 plan §6, confirmed decision #1) — a deduction
     * attempted from {@code HELD} (or any other status) is rejected with
     * {@link ConflictException}, exactly like every other illegal transition,
     * via {@link DepositCase#deduct(long)}'s own guard. An invalid amount (zero,
     * negative, or exceeding the remaining deposit) is rejected with
     * {@link ValidationException} instead — a distinct, non-state failure mode,
     * mirrored on {@code booking_status_history}'s equivalent
     * {@code IllegalArgumentException -> ValidationException} mapping in
     * {@code BookingService.saveBooking}.
     */
    @Transactional
    public DepositCase deduct(UUID depositId, long amountMinor, String reason) {
        DepositCase deposit = requireDeposit(depositId);
        DepositStatus previousStatus = deposit.getStatus();
        try {
            deposit.deduct(amountMinor);
        } catch (IllegalStateException ex) {
            throw new ConflictException(ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(ex.getMessage());
        }
        return persistTransition(deposit, DepositTransactionType.DEDUCT, previousStatus, amountMinor, normalizeReason(reason));
    }

    @Transactional(readOnly = true)
    public DepositCase getDeposit(UUID depositId) {
        return requireDeposit(depositId);
    }

    @Transactional(readOnly = true)
    public DepositCase getDepositByBooking(UUID bookingId) {
        return depositRepository.findByBookingId(Objects.requireNonNull(bookingId, "bookingId"))
                .orElseThrow(() -> new ResourceNotFoundException("Deposit not found for booking"));
    }

    private DepositCase persistTransition(DepositCase deposit, DepositTransactionType transactionType, DepositStatus previousStatus, long amountMinor, String reason) {
        DepositCase saved = depositRepository.save(deposit);
        depositTransactionRepository.save(new DepositTransaction(
                saved, transactionType, previousStatus, saved.getStatus(), amountMinor, reason
        ));
        return saved;
    }

    private Booking requireBooking(UUID bookingId) {
        return bookingRepository.findById(Objects.requireNonNull(bookingId, "bookingId"))
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
    }

    private DepositCase requireDeposit(UUID depositId) {
        return depositRepository.findById(Objects.requireNonNull(depositId, "depositId"))
                .orElseThrow(() -> new ResourceNotFoundException("Deposit not found"));
    }

    private void requireRenterActor(Booking booking, UUID actorAccountId) {
        IdentityAccount actor = identityAccountRepository.findById(Objects.requireNonNull(actorAccountId, "actorAccountId"))
                .orElseThrow(() -> new ResourceNotFoundException("Actor account not found"));
        if (!actor.getId().equals(booking.getRenterId())) {
            throw new ForbiddenException("Deposit can only be held for the booking renter");
        }
    }

    private void applyTransition(Runnable transition) {
        try {
            transition.run();
        } catch (IllegalStateException ex) {
            throw new ConflictException(ex.getMessage());
        }
    }

    private String normalizeReason(String reason) {
        if (reason == null) {
            return null;
        }
        String normalized = reason.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
