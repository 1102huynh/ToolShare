CREATE TABLE identity_accounts (
    id UUID NOT NULL,
    email VARCHAR(255) NOT NULL CHECK (TRIM(email) <> ''),
    password_hash VARCHAR(255) NOT NULL,
    state VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_login_at TIMESTAMP WITH TIME ZONE,
    verified_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_identity_accounts PRIMARY KEY (id)
);

CREATE UNIQUE INDEX idx_identity_account_email_normalized ON identity_accounts (email);

CREATE TABLE verification_challenges (
    id UUID NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    identity_account_id UUID NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_verification_challenges PRIMARY KEY (id),
    CONSTRAINT fk_verification_challenges_account FOREIGN KEY (identity_account_id) REFERENCES identity_accounts (id)
);

CREATE INDEX idx_verification_challenges_account ON verification_challenges (identity_account_id);
CREATE INDEX idx_verification_challenges_token_hash ON verification_challenges (token_hash);

CREATE TABLE refresh_tokens (
    id UUID NOT NULL,
    token_id UUID NOT NULL,
    identity_account_id UUID NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    replaced_by_token_id UUID,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_token_id UNIQUE (token_id),
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_account FOREIGN KEY (identity_account_id) REFERENCES identity_accounts (id)
);

CREATE INDEX idx_refresh_tokens_account ON refresh_tokens (identity_account_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);
