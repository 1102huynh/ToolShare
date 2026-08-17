package com.toolshare.payment.domain;

public enum PaymentStatus {
    INITIATED,
    AUTHORIZED,
    CAPTURED,
    SETTLED,
    FAILED,
    CANCELLED,
    REFUNDED,
    PARTIALLY_REFUNDED;

    public boolean canTransitionTo(PaymentStatus next) {
        return switch (this) {
            case INITIATED -> next == AUTHORIZED || next == FAILED || next == CANCELLED;
            case AUTHORIZED -> next == CAPTURED || next == FAILED || next == CANCELLED;
            case CAPTURED -> next == SETTLED || next == REFUNDED || next == PARTIALLY_REFUNDED;
            case SETTLED, FAILED, CANCELLED, REFUNDED, PARTIALLY_REFUNDED -> false;
        };
    }
}
