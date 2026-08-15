CREATE TABLE bookings (
    id UUID NOT NULL,
    listing_id UUID NOT NULL,
    renter_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    requested_start_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    requested_end_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    booking_policy VARCHAR(32) NOT NULL,
    state VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_bookings PRIMARY KEY (id),
    CONSTRAINT ck_bookings_time_range_valid CHECK (requested_start_at < requested_end_at),
    CONSTRAINT fk_bookings_listing FOREIGN KEY (listing_id) REFERENCES tool_listings (id),
    CONSTRAINT fk_bookings_renter FOREIGN KEY (renter_id) REFERENCES identity_accounts (id),
    CONSTRAINT fk_bookings_owner FOREIGN KEY (owner_id) REFERENCES identity_accounts (id)
);

CREATE TABLE booking_status_history (
    id UUID NOT NULL,
    booking_id UUID NOT NULL,
    previous_state VARCHAR(32),
    new_state VARCHAR(32) NOT NULL,
    actor_account_id UUID,
    reason VARCHAR(500),
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_booking_status_history PRIMARY KEY (id),
    CONSTRAINT fk_booking_status_history_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_booking_status_history_actor FOREIGN KEY (actor_account_id) REFERENCES identity_accounts (id)
);

CREATE INDEX idx_bookings_listing_state_time ON bookings (listing_id, state, requested_start_at, requested_end_at);
CREATE INDEX idx_bookings_renter ON bookings (renter_id);
CREATE INDEX idx_bookings_owner ON bookings (owner_id);
CREATE INDEX idx_booking_status_history_booking_time ON booking_status_history (booking_id, changed_at);
