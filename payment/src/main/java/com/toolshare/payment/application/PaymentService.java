package com.toolshare.payment.application;

import com.toolshare.booking.domain.Booking;
import com.toolshare.booking.infrastructure.persistence.BookingRepository;
import com.toolshare.common.exception.ConflictException;
import com.toolshare.common.exception.ForbiddenException;
import com.toolshare.common.exception.ResourceNotFoundException;
import com.toolshare.identity.domain.IdentityAccount;
import com.toolshare.identity.infrastructure.persistence.IdentityAccountRepository;
import com.toolshare.listing.domain.ToolListing;
import com.toolshare.listing.infrastructure.persistence.ToolListingRepository;
import com.toolshare.payment.domain.PaymentOrder;
import com.toolshare.payment.domain.PaymentStatus;
import com.toolshare.payment.domain.PaymentTransaction;
import com.toolshare.payment.domain.PaymentTransactionType;
import com.toolshare.payment.infrastructure.persistence.PaymentRepository;
import com.toolshare.payment.infrastructure.persistence.PaymentTransactionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class PaymentService {

    private static final long MINUTES_PER_DAY = 24L * 60L;

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final BookingRepository bookingRepository;
    private final ToolListingRepository toolListingRepository;
    private final IdentityAccountRepository identityAccountRepository;
    private final PaymentProvider paymentProvider;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            BookingRepository bookingRepository,
            ToolListingRepository toolListingRepository,
            IdentityAccountRepository identityAccountRepository,
            PaymentProvider paymentProvider
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.bookingRepository = bookingRepository;
        this.toolListingRepository = toolListingRepository;
        this.identityAccountRepository = identityAccountRepository;
        this.paymentProvider = paymentProvider;
    }

    @Transactional
    public PaymentOrder createPaymentIntent(UUID bookingId, UUID actorAccountId) {
        Booking booking = requireBooking(bookingId);
        requireRenterActor(booking, actorAccountId);

        if (paymentRepository.findByBookingId(booking.getId()).isPresent()) {
            throw new ConflictException("Payment already exists for this booking");
        }

        ToolListing listing = toolListingRepository.findById(booking.getListingId())
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));

        long amountMinor = computeAmountMinor(listing, booking);

        PaymentOrder payment;
        try {
            payment = paymentRepository.saveAndFlush(PaymentOrder.initiate(
                    booking.getId(),
                    booking.getRenterId(),
                    amountMinor,
                    listing.getCurrency(),
                    paymentProvider.providerName()
            ));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Payment already exists for this booking");
        }

        ProviderIntentResult intentResult = paymentProvider.createIntent(payment.getId(), amountMinor, listing.getCurrency());
        if (!intentResult.success()) {
            applyTransition(payment::fail);
            PaymentOrder failed = paymentRepository.save(payment);
            paymentTransactionRepository.save(new PaymentTransaction(
                    failed, PaymentTransactionType.FAIL, PaymentStatus.INITIATED, failed.getStatus(), amountMinor, null
            ));
            return failed;
        }

        payment.attachProviderReference(intentResult.providerReference());
        PaymentOrder saved = paymentRepository.save(payment);
        paymentTransactionRepository.save(new PaymentTransaction(
                saved, PaymentTransactionType.CREATE, null, saved.getStatus(), amountMinor, saved.getProviderReference()
        ));
        return saved;
    }

    @Transactional
    public PaymentOrder authorizePayment(UUID paymentId) {
        PaymentOrder payment = requirePayment(paymentId);
        PaymentStatus previousStatus = payment.getStatus();
        requireTransitionAllowed(payment, PaymentStatus.AUTHORIZED);

        ProviderResult result = paymentProvider.authorize(payment.getProviderReference());
        PaymentTransactionType recordedType;
        if (result.success()) {
            applyTransition(payment::authorize);
            recordedType = PaymentTransactionType.AUTHORIZE;
        } else {
            applyTransition(payment::fail);
            recordedType = PaymentTransactionType.FAIL;
        }

        return persistTransition(payment, recordedType, previousStatus);
    }

    @Transactional
    public PaymentOrder capturePayment(UUID paymentId) {
        PaymentOrder payment = requirePayment(paymentId);
        PaymentStatus previousStatus = payment.getStatus();
        requireTransitionAllowed(payment, PaymentStatus.CAPTURED);

        ProviderResult result = paymentProvider.capture(payment.getProviderReference());
        PaymentTransactionType recordedType;
        if (result.success()) {
            applyTransition(payment::capture);
            recordedType = PaymentTransactionType.CAPTURE;
        } else {
            applyTransition(payment::fail);
            recordedType = PaymentTransactionType.FAIL;
        }

        return persistTransition(payment, recordedType, previousStatus);
    }

    @Transactional
    public PaymentOrder cancelPayment(UUID paymentId) {
        PaymentOrder payment = requirePayment(paymentId);
        PaymentStatus previousStatus = payment.getStatus();
        requireTransitionAllowed(payment, PaymentStatus.CANCELLED);

        ProviderResult result = paymentProvider.cancel(payment.getProviderReference());
        PaymentTransactionType recordedType;
        if (result.success()) {
            applyTransition(payment::cancel);
            recordedType = PaymentTransactionType.CANCEL;
        } else {
            applyTransition(payment::fail);
            recordedType = PaymentTransactionType.FAIL;
        }

        return persistTransition(payment, recordedType, previousStatus);
    }

    /**
     * Direct, operator/support-initiated failure — unlike authorize/capture/cancel
     * there is no matching {@link PaymentProvider} method to call (the port has no
     * "fail" operation), so this is a pure domain-level guarded transition.
     * {@code reason} is accepted to match the plan's documented signature but is
     * NOT persisted anywhere: {@code payment_transactions} has no reason column in
     * T-011's migration scope (unlike {@code booking_status_history.reason}).
     */
    @Transactional
    public PaymentOrder failPayment(UUID paymentId, String reason) {
        PaymentOrder payment = requirePayment(paymentId);
        PaymentStatus previousStatus = payment.getStatus();
        applyTransition(payment::fail);
        return persistTransition(payment, PaymentTransactionType.FAIL, previousStatus);
    }

    @Transactional(readOnly = true)
    public PaymentOrder getPayment(UUID paymentId) {
        return requirePayment(paymentId);
    }

    @Transactional(readOnly = true)
    public PaymentOrder getPaymentByBooking(UUID bookingId) {
        return paymentRepository.findByBookingId(Objects.requireNonNull(bookingId, "bookingId"))
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for booking"));
    }

    private PaymentOrder persistTransition(PaymentOrder payment, PaymentTransactionType transactionType, PaymentStatus previousStatus) {
        PaymentOrder saved = paymentRepository.save(payment);
        paymentTransactionRepository.save(new PaymentTransaction(
                saved, transactionType, previousStatus, saved.getStatus(), saved.getAmountMinor(), saved.getProviderReference()
        ));
        return saved;
    }

    private long computeAmountMinor(ToolListing listing, Booking booking) {
        long days = ceilDays(booking.getRequestedStartAt(), booking.getRequestedEndAt());
        return listing.getDailyRateAmount() * days;
    }

    private static long ceilDays(LocalDateTime start, LocalDateTime end) {
        long totalMinutes = Duration.between(start, end).toMinutes();
        return (totalMinutes + MINUTES_PER_DAY - 1) / MINUTES_PER_DAY;
    }

    private Booking requireBooking(UUID bookingId) {
        return bookingRepository.findById(Objects.requireNonNull(bookingId, "bookingId"))
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
    }

    private PaymentOrder requirePayment(UUID paymentId) {
        return paymentRepository.findById(Objects.requireNonNull(paymentId, "paymentId"))
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
    }

    private void requireRenterActor(Booking booking, UUID actorAccountId) {
        IdentityAccount actor = identityAccountRepository.findById(Objects.requireNonNull(actorAccountId, "actorAccountId"))
                .orElseThrow(() -> new ResourceNotFoundException("Actor account not found"));
        if (!actor.getId().equals(booking.getRenterId())) {
            throw new ForbiddenException("Payment can only be initiated by the booking renter");
        }
    }

    private void applyTransition(Runnable transition) {
        try {
            transition.run();
        } catch (IllegalStateException ex) {
            throw new ConflictException(ex.getMessage());
        }
    }

    /**
     * Validates the requested state transition BEFORE the external
     * {@link PaymentProvider} is called, so an illegal transition (e.g.
     * capturing a payment that was never authorized) never results in a
     * provider round-trip. The actual mutation still happens later via
     * {@link PaymentOrder}'s own guarded transition methods; this is a
     * read-only pre-check using the same {@code canTransitionTo} rule.
     */
    private void requireTransitionAllowed(PaymentOrder payment, PaymentStatus target) {
        if (!payment.getStatus().canTransitionTo(target)) {
            throw new ConflictException("Payment cannot transition from " + payment.getStatus() + " to " + target);
        }
    }
}
