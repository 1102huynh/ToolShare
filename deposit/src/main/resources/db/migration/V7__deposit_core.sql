CREATE TABLE deposits (
    id UUID NOT NULL,
    booking_id UUID NOT NULL,
    renter_id UUID NOT NULL,
    amount_minor BIGINT NOT NULL,
    deducted_amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_deposits PRIMARY KEY (id),
    CONSTRAINT uq_deposits_booking UNIQUE (booking_id),
    CONSTRAINT ck_deposits_amount_non_negative CHECK (amount_minor >= 0),
    CONSTRAINT ck_deposits_deducted_non_negative CHECK (deducted_amount_minor >= 0),
    CONSTRAINT ck_deposits_deducted_le_amount CHECK (deducted_amount_minor <= amount_minor),
    CONSTRAINT fk_deposits_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_deposits_renter FOREIGN KEY (renter_id) REFERENCES identity_accounts (id)
);

CREATE TABLE deposit_transactions (
    id UUID NOT NULL,
    deposit_id UUID NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    previous_status VARCHAR(32),
    new_status VARCHAR(32) NOT NULL,
    amount_minor BIGINT NOT NULL,
    reason VARCHAR(255),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_deposit_transactions PRIMARY KEY (id),
    CONSTRAINT ck_deposit_transactions_amount_non_negative CHECK (amount_minor >= 0),
    CONSTRAINT fk_deposit_transactions_deposit FOREIGN KEY (deposit_id) REFERENCES deposits (id)
);

CREATE INDEX idx_deposits_status ON deposits (status);
CREATE INDEX idx_deposit_transactions_deposit_time ON deposit_transactions (deposit_id, occurred_at);
