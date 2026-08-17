CREATE TABLE payments (
    id UUID NOT NULL,
    booking_id UUID NOT NULL,
    renter_id UUID NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider_name VARCHAR(64) NOT NULL,
    provider_reference VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT uq_payments_booking UNIQUE (booking_id),
    CONSTRAINT ck_payments_amount_non_negative CHECK (amount_minor >= 0),
    CONSTRAINT fk_payments_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_payments_renter FOREIGN KEY (renter_id) REFERENCES identity_accounts (id)
);

CREATE TABLE payment_transactions (
    id UUID NOT NULL,
    payment_id UUID NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    previous_status VARCHAR(32),
    new_status VARCHAR(32) NOT NULL,
    amount_minor BIGINT NOT NULL,
    provider_reference VARCHAR(128),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_payment_transactions PRIMARY KEY (id),
    CONSTRAINT ck_payment_transactions_amount_non_negative CHECK (amount_minor >= 0),
    CONSTRAINT fk_payment_transactions_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
);

CREATE INDEX idx_payments_status ON payments (status);
CREATE INDEX idx_payment_transactions_payment_time ON payment_transactions (payment_id, occurred_at);
