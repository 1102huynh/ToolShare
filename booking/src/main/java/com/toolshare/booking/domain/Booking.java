package com.toolshare.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "bookings",
        indexes = {
                @Index(name = "idx_bookings_listing_state_time", columnList = "listing_id,state,requested_start_at,requested_end_at"),
                @Index(name = "idx_bookings_renter", columnList = "renter_id"),
                @Index(name = "idx_bookings_owner", columnList = "owner_id")
        }
)
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(name = "renter_id", nullable = false)
    private UUID renterId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "requested_start_at", nullable = false)
    private LocalDateTime requestedStartAt;

    @Column(name = "requested_end_at", nullable = false)
    private LocalDateTime requestedEndAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_policy", nullable = false)
    private BookingPolicy bookingPolicy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingState state;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Booking() {
    }

    private Booking(
            UUID listingId,
            UUID renterId,
            UUID ownerId,
            LocalDateTime requestedStartAt,
            LocalDateTime requestedEndAt,
            BookingPolicy bookingPolicy,
            BookingState initialState
    ) {
        this.listingId = Objects.requireNonNull(listingId, "listingId");
        this.renterId = Objects.requireNonNull(renterId, "renterId");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.requestedStartAt = requireStart(requestedStartAt);
        this.requestedEndAt = requireEnd(requestedEndAt, this.requestedStartAt);
        this.bookingPolicy = Objects.requireNonNull(bookingPolicy, "bookingPolicy");
        this.state = Objects.requireNonNull(initialState, "initialState");
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static Booking create(
            UUID listingId,
            UUID renterId,
            UUID ownerId,
            LocalDateTime requestedStartAt,
            LocalDateTime requestedEndAt,
            BookingPolicy bookingPolicy
    ) {
        BookingPolicy policy = Objects.requireNonNull(bookingPolicy, "bookingPolicy");
        BookingState initialState = policy == BookingPolicy.OWNER_APPROVAL
                ? BookingState.REQUESTED
                : BookingState.PAYMENT_PENDING;

        return new Booking(
                listingId,
                renterId,
                ownerId,
                requestedStartAt,
                requestedEndAt,
                policy,
                initialState
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getListingId() {
        return listingId;
    }

    public UUID getRenterId() {
        return renterId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public LocalDateTime getRequestedStartAt() {
        return requestedStartAt;
    }

    public LocalDateTime getRequestedEndAt() {
        return requestedEndAt;
    }

    public BookingPolicy getBookingPolicy() {
        return bookingPolicy;
    }

    public BookingState getState() {
        return state;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void approve() {
        transitionTo(BookingState.PAYMENT_PENDING);
    }

    public void reject() {
        transitionTo(BookingState.REJECTED);
    }

    public void cancel() {
        transitionTo(BookingState.CANCELLED);
    }

    public void expire() {
        transitionTo(BookingState.EXPIRED);
    }

    public void confirm() {
        transitionTo(BookingState.CONFIRMED);
    }

    public void markReadyForPickup() {
        transitionTo(BookingState.READY_FOR_PICKUP);
    }

    public void startRental() {
        transitionTo(BookingState.IN_RENTAL);
    }

    public void markReturnPending() {
        transitionTo(BookingState.RETURN_PENDING);
    }

    public void complete() {
        transitionTo(BookingState.COMPLETED);
    }

    public void markDisputed() {
        transitionTo(BookingState.DISPUTED);
    }

    private void transitionTo(BookingState next) {
        Objects.requireNonNull(next, "next");
        if (!state.canTransitionTo(next)) {
            throw new IllegalStateException("Booking cannot transition from " + state + " to " + next);
        }
        this.state = next;
        this.updatedAt = OffsetDateTime.now();
    }

    private static LocalDateTime requireStart(LocalDateTime value) {
        return Objects.requireNonNull(value, "requestedStartAt");
    }

    private static LocalDateTime requireEnd(LocalDateTime end, LocalDateTime start) {
        LocalDateTime value = Objects.requireNonNull(end, "requestedEndAt");
        if (!value.isAfter(start)) {
            throw new IllegalArgumentException("Requested booking time range is invalid");
        }
        return value;
    }
}
