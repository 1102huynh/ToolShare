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

        ToolListing listing = toolListingRepository.findById(Objects.requireNonNull(listingId, "listingId"))
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
