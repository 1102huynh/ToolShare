package com.toolshare.payment.domain;

public enum PaymentTransactionType {
    /**
     * Records the creation of the payment intent itself (previousStatus = null,
     * newStatus = INITIATED). Not one of the four types named in the T-011 plan's
     * PaymentProvider sketch (AUTHORIZE | CAPTURE | FAIL | CANCEL | REFUND) — added
     * to represent the one ledger event the plan's flow requires but its type list
     * didn't enumerate: the initial payments row itself needs a transaction_type.
     */
    CREATE,
    AUTHORIZE,
    CAPTURE,
    FAIL,
    CANCEL,
    REFUND
}
