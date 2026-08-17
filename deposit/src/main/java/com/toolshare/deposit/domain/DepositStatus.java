package com.toolshare.deposit.domain;

public enum DepositStatus {
    REQUESTED,
    AUTHORIZED,
    HELD,
    RELEASE_PENDING,
    RELEASED,
    PARTIALLY_DEDUCTED,
    FULLY_DEDUCTED,
    DISPUTED;

    /**
     * T-013 plan §6 transition table. Deduction ({@code PARTIALLY_DEDUCTED}/
     * {@code FULLY_DEDUCTED}) is reachable ONLY from {@code DISPUTED} — confirmed
     * decision #1. There is no {@code HELD -> *_DEDUCTED} edge.
     */
    public boolean canTransitionTo(DepositStatus next) {
        return switch (this) {
            case REQUESTED -> next == AUTHORIZED;
            case AUTHORIZED -> next == HELD;
            case HELD -> next == RELEASE_PENDING || next == DISPUTED;
            case RELEASE_PENDING -> next == RELEASED || next == DISPUTED;
            case DISPUTED -> next == PARTIALLY_DEDUCTED || next == FULLY_DEDUCTED || next == RELEASE_PENDING;
            case PARTIALLY_DEDUCTED -> next == RELEASED;
            case RELEASED, FULLY_DEDUCTED -> false;
        };
    }
}
