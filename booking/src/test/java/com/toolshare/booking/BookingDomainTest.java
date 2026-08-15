package com.toolshare.booking;

import com.toolshare.booking.domain.Booking;
import com.toolshare.booking.domain.BookingPolicy;
import com.toolshare.booking.domain.BookingState;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookingDomainTest {

    @Test
    void owner_approval_booking_starts_in_requested_state() {
        Booking booking = createOwnerApprovalBooking();

        assertEquals(BookingState.REQUESTED, booking.getState());
    }

    @Test
    void instant_booking_starts_in_payment_pending_state() {
        Booking booking = Booking.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusHours(2),
                BookingPolicy.INSTANT
        );

        assertEquals(BookingState.PAYMENT_PENDING, booking.getState());
    }

    @Test
    void valid_lifecycle_transitions_reach_completed() {
        Booking booking = createOwnerApprovalBooking();

        booking.approve();
        booking.confirm();
        booking.markReadyForPickup();
        booking.startRental();
        booking.markReturnPending();
        booking.complete();

        assertEquals(BookingState.COMPLETED, booking.getState());
    }

    @Test
    void invalid_transition_is_rejected() {
        Booking booking = createOwnerApprovalBooking();

        IllegalStateException exception = assertThrows(IllegalStateException.class, booking::complete);

        assertEquals("Booking cannot transition from REQUESTED to COMPLETED", exception.getMessage());
    }

    @Test
    void terminal_state_rejects_further_changes() {
        Booking booking = createOwnerApprovalBooking();
        booking.reject();

        IllegalStateException exception = assertThrows(IllegalStateException.class, booking::cancel);

        assertEquals("Booking cannot transition from REJECTED to CANCELLED", exception.getMessage());
    }

    @Test
    void requested_booking_can_expire() {
        Booking booking = createOwnerApprovalBooking();

        booking.expire();

        assertEquals(BookingState.EXPIRED, booking.getState());
    }

    private Booking createOwnerApprovalBooking() {
        LocalDateTime start = LocalDateTime.now().plusDays(2).withMinute(0).withSecond(0).withNano(0);
        return Booking.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                start,
                start.plusHours(4),
                BookingPolicy.OWNER_APPROVAL
        );
    }
}
