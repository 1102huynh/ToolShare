CREATE TABLE webhook_events (
    id UUID NOT NULL,
    provider_name VARCHAR(64) NOT NULL,
    provider_event_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payment_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    outcome_reason VARCHAR(255),
    payload TEXT NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_webhook_events PRIMARY KEY (id),
    CONSTRAINT uq_webhook_events_provider_event UNIQUE (provider_name, provider_event_id),
    CONSTRAINT fk_webhook_events_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
);

CREATE INDEX idx_webhook_events_payment ON webhook_events (payment_id);
