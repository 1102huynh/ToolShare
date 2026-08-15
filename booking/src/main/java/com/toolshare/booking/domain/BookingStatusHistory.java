package com.toolshare.booking.domain;

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
        name = "booking_status_history",
        indexes = {
                @Index(name = "idx_booking_status_history_booking_time", columnList = "booking_id,changed_at")
        }
)
public class BookingStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_state")
    private BookingState previousState;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_state", nullable = false)
    private BookingState newState;

    @Column(name = "actor_account_id")
    private UUID actorAccountId;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt;

    protected BookingStatusHistory() {
    }

    public BookingStatusHistory(
            Booking booking,
            BookingState previousState,
            BookingState newState,
            UUID actorAccountId,
            String reason
    ) {
        this.booking = Objects.requireNonNull(booking, "booking");
        this.previousState = previousState;
        this.newState = Objects.requireNonNull(newState, "newState");
        this.actorAccountId = actorAccountId;
        this.reason = reason == null ? null : reason.trim();
        this.changedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public Booking getBooking() {
        return booking;
    }

    public BookingState getPreviousState() {
        return previousState;
    }

    public BookingState getNewState() {
        return newState;
    }

    public UUID getActorAccountId() {
        return actorAccountId;
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getChangedAt() {
        return changedAt;
    }
}
