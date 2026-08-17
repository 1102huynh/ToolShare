package com.toolshare.deposit.domain;

public enum DepositTransactionType {
    /**
     * Records the creation of the deposit case itself (previousStatus = null,
     * newStatus = REQUESTED). Mirrors {@code PaymentTransactionType.CREATE}'s
     * T-012 elaboration — the DOMAIN-MODEL event list implies but does not
     * enumerate the ledger row for a deposit's own opening.
     */
    OPEN,
    AUTHORIZE,
    HOLD,
    SCHEDULE_RELEASE,
    RELEASE,
    DEDUCT,
    DISPUTE
}
