package com.toolshare.booking.domain;

import java.util.EnumSet;
import java.util.Set;

public enum BookingState {
    REQUESTED,
    PAYMENT_PENDING,
    CONFIRMED,
    READY_FOR_PICKUP,
    IN_RENTAL,
    RETURN_PENDING,
    COMPLETED,
    CANCELLED,
    EXPIRED,
    DISPUTED,
    REJECTED;

    private static final Set<BookingState> OVERLAP_BLOCKING_STATES = EnumSet.of(
            REQUESTED,
            PAYMENT_PENDING,
            CONFIRMED,
            READY_FOR_PICKUP,
            IN_RENTAL,
            RETURN_PENDING,
            DISPUTED
    );

    public boolean canTransitionTo(BookingState next) {
        return switch (this) {
            case REQUESTED -> next == PAYMENT_PENDING || next == REJECTED || next == CANCELLED || next == EXPIRED;
            case PAYMENT_PENDING -> next == CONFIRMED || next == CANCELLED;
            case CONFIRMED -> next == READY_FOR_PICKUP || next == CANCELLED;
            case READY_FOR_PICKUP -> next == IN_RENTAL || next == CANCELLED;
            case IN_RENTAL -> next == RETURN_PENDING;
            case RETURN_PENDING -> next == COMPLETED || next == DISPUTED;
            case DISPUTED -> next == COMPLETED;
            case COMPLETED, CANCELLED, EXPIRED, REJECTED -> false;
        };
    }

    public static Set<BookingState> overlapBlockingStates() {
        return EnumSet.copyOf(OVERLAP_BLOCKING_STATES);
    }
}
