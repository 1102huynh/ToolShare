package com.toolshare.booking.application;

import com.toolshare.booking.domain.Booking;
import com.toolshare.booking.domain.BookingPolicy;
import com.toolshare.booking.domain.BookingState;
import com.toolshare.booking.domain.BookingStatusHistory;
import com.toolshare.booking.infrastructure.persistence.BookingRepository;
import com.toolshare.booking.infrastructure.persistence.BookingStatusHistoryRepository;
import com.toolshare.common.exception.ConflictException;
import com.toolshare.common.exception.ForbiddenException;
import com.toolshare.common.exception.ResourceNotFoundException;
import com.toolshare.common.exception.ValidationException;
import com.toolshare.identity.infrastructure.persistence.IdentityAccountRepository;
import com.toolshare.listing.domain.ListingStatus;
import com.toolshare.listing.domain.ToolListing;
import com.toolshare.listing.infrastructure.persistence.ToolListingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingStatusHistoryRepository bookingStatusHistoryRepository;
    private final ToolListingRepository toolListingRepository;
    private final IdentityAccountRepository identityAccountRepository;

    public BookingService(
            BookingRepository bookingRepository,
            BookingStatusHistoryRepository bookingStatusHistoryRepository,
            ToolListingRepository toolListingRepository,
            IdentityAccountRepository identityAccountRepository
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingStatusHistoryRepository = bookingStatusHistoryRepository;
        this.toolListingRepository = toolListingRepository;
        this.identityAccountRepository = identityAccountRepository;
    }

    @Transactional
    public Booking createBooking(
            UUID listingId,
            UUID renterId,
            LocalDateTime requestedStartAt,
            LocalDateTime requestedEndAt,
            BookingPolicy bookingPolicy
    ) {
        requireFutureStart(requestedStartAt);
        requireAccount(renterId, "Renter account not found");
        Objects.requireNonNull(listingId, "listingId");

        // Acquire an exclusive row lock on the listing before re-checking for overlaps.
        // This is the serialization point for booking creation: it is held for the rest
        // of this transaction, so a concurrent createBooking call for the same listing
        // blocks here until this transaction commits or rolls back, then re-evaluates
        // the overlap check against whatever this transaction actually persisted.
        ToolListing listing = toolListingRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));

        if (listing.getStatus() != ListingStatus.ACTIVE) {
            throw new ConflictException("Listing is not bookable");
        }

        UUID ownerId = listing.getOwnerAccountId();
        requireAccount(ownerId, "Owner account not found");

        if (hasOverlapConflict(listingId, requestedStartAt, requestedEndAt)) {
            throw new ConflictException("Booking overlaps with an existing active booking");
        }

        Booking saved = saveBooking(listingId, renterId, ownerId, requestedStartAt, requestedEndAt, bookingPolicy);
        bookingStatusHistoryRepository.save(new BookingStatusHistory(saved, null, saved.getState(), renterId, null));
        return saved;
    }

    @Transactional(readOnly = true)
    public Booking getBooking(UUID bookingId) {
        return bookingRepository.findById(Objects.requireNonNull(bookingId, "bookingId"))
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
    }

    @Transactional
    public Booking approveBooking(UUID bookingId, UUID actorAccountId) {
        Booking booking = requireBooking(bookingId);
        requireOwnerActor(booking, actorAccountId);

        BookingState previousState = booking.getState();
        applyTransition(booking::approve);
        Booking saved = bookingRepository.save(booking);
        bookingStatusHistoryRepository.save(new BookingStatusHistory(saved, previousState, saved.getState(), actorAccountId, null));
        return saved;
    }

    @Transactional
    public Booking rejectBooking(UUID bookingId, UUID actorAccountId, String reason) {
        Booking booking = requireBooking(bookingId);
        requireOwnerActor(booking, actorAccountId);

        BookingState previousState = booking.getState();
        applyTransition(booking::reject);
        Booking saved = bookingRepository.save(booking);
        bookingStatusHistoryRepository.save(new BookingStatusHistory(saved, previousState, saved.getState(), actorAccountId, normalizeReason(reason)));
        return saved;
    }

    @Transactional
    public Booking cancelBooking(UUID bookingId, UUID actorAccountId, String reason) {
        Booking booking = requireBooking(bookingId);
        UUID actorId = requireAccount(actorAccountId, "Actor account not found").getId();
        if (!actorId.equals(booking.getRenterId()) && !actorId.equals(booking.getOwnerId())) {
            throw new ForbiddenException("Booking participant is required");
        }

        BookingState previousState = booking.getState();
        applyTransition(booking::cancel);
        Booking saved = bookingRepository.save(booking);
        bookingStatusHistoryRepository.save(new BookingStatusHistory(saved, previousState, saved.getState(), actorId, normalizeReason(reason)));
        return saved;
    }

    @Transactional
    public Booking expirePendingBooking(UUID bookingId) {
        Booking booking = requireBooking(bookingId);

        BookingState previousState = booking.getState();
        applyTransition(booking::expire);
        Booking saved = bookingRepository.save(booking);
        bookingStatusHistoryRepository.save(new BookingStatusHistory(saved, previousState, saved.getState(), null, "AUTO_EXPIRED"));
        return saved;
    }

    /**
     * T-012 addition (plan §5): system-triggered, no-human-actor transition, called
     * by {@code payment.application.PaymentWebhookService} when a webhook-driven
     * {@code AUTHORIZED -> CAPTURED} payment transition is applied. Same shape as
     * {@link #expirePendingBooking(UUID)} — {@code actorAccountId = null}, fixed
     * {@code reason}. The only addition this task makes to {@code BookingService};
     * every other method here is unchanged.
     * <p>
     * T-012 review fix: {@code REQUIRES_NEW}, not the default {@code REQUIRED}. The
     * caller ({@code PaymentWebhookService.confirmBookingAfterCapture}) catches and
     * swallows a failure here so the webhook can still ack 200 with the payment left
     * CAPTURED — but catching an exception does not clear Spring's rollback-only flag
     * on a transaction this method merely *participated* in. With the default
     * propagation, a failure here would mark the caller's own (already-committed-in-
     * intent) transaction rollback-only, so the outer commit would throw
     * {@code UnexpectedRollbackException} and silently discard the payment capture
     * and the webhook_events row along with it — the opposite of the documented
     * behavior. {@code REQUIRES_NEW} gives this method its own physical transaction,
     * so a failure here rolls back only its own (empty, since {@code applyTransition}
     * throws before any save) work, leaving the caller's transaction free to commit.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Booking confirmAfterPayment(UUID bookingId) {
        Booking booking = requireBooking(bookingId);

        BookingState previousState = booking.getState();
        applyTransition(booking::confirm);
        Booking saved = bookingRepository.save(booking);
        bookingStatusHistoryRepository.save(new BookingStatusHistory(saved, previousState, saved.getState(), null, "PAYMENT_CAPTURED"));
        return saved;
    }

    private Booking saveBooking(
            UUID listingId,
            UUID renterId,
            UUID ownerId,
            LocalDateTime requestedStartAt,
            LocalDateTime requestedEndAt,
            BookingPolicy bookingPolicy
    ) {
        try {
            return bookingRepository.save(Booking.create(
                    listingId,
                    renterId,
                    ownerId,
                    requestedStartAt,
                    requestedEndAt,
                    bookingPolicy
            ));
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(ex.getMessage());
        }
    }

    private boolean hasOverlapConflict(UUID listingId, LocalDateTime requestedStartAt, LocalDateTime requestedEndAt) {
        List<BookingState> overlapBlockingStates = List.copyOf(BookingState.overlapBlockingStates());
        List<Booking> overlapping = bookingRepository.findOverlappingByListingAndStates(
                listingId,
                requestedStartAt,
                requestedEndAt,
                overlapBlockingStates
        );
        return !overlapping.isEmpty();
    }

    private com.toolshare.identity.domain.IdentityAccount requireAccount(UUID accountId, String notFoundMessage) {
        return identityAccountRepository.findById(Objects.requireNonNull(accountId, "accountId"))
                .orElseThrow(() -> new ResourceNotFoundException(notFoundMessage));
    }

    private Booking requireBooking(UUID bookingId) {
        return bookingRepository.findById(Objects.requireNonNull(bookingId, "bookingId"))
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
    }

    private void requireOwnerActor(Booking booking, UUID actorAccountId) {
        UUID actorId = requireAccount(actorAccountId, "Actor account not found").getId();
        if (!booking.getOwnerId().equals(actorId)) {
            throw new ForbiddenException("Booking owner decision is required");
        }
    }

    private void requireFutureStart(LocalDateTime requestedStartAt) {
        LocalDateTime start = Objects.requireNonNull(requestedStartAt, "requestedStartAt");
        if (start.isBefore(LocalDateTime.now())) {
            throw new ValidationException("Booking start time cannot be in the past");
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
