CREATE TABLE identity_account_roles (
    identity_account_id UUID NOT NULL,
    role VARCHAR(32) NOT NULL,
    CONSTRAINT pk_identity_account_roles PRIMARY KEY (identity_account_id, role),
    CONSTRAINT fk_identity_account_roles_account FOREIGN KEY (identity_account_id) REFERENCES identity_accounts (id)
);

CREATE INDEX idx_identity_account_roles_account ON identity_account_roles (identity_account_id);
CREATE INDEX idx_identity_account_roles_role ON identity_account_roles (role);
