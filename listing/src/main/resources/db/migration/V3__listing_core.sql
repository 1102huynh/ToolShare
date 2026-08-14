CREATE TABLE categories (
    id UUID NOT NULL,
    category_key VARCHAR(100) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT uq_categories_key UNIQUE (category_key)
);

CREATE TABLE locations (
    id UUID NOT NULL,
    address_line_1 VARCHAR(255) NOT NULL,
    address_line_2 VARCHAR(255),
    ward VARCHAR(255) NOT NULL,
    district VARCHAR(255) NOT NULL,
    city VARCHAR(255) NOT NULL,
    country_code VARCHAR(2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_locations PRIMARY KEY (id)
);

CREATE TABLE tool_listings (
    id UUID NOT NULL,
    owner_account_id UUID NOT NULL,
    category_id UUID NOT NULL,
    location_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    daily_rate_amount BIGINT NOT NULL,
    deposit_amount BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    delivery_available BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_tool_listings PRIMARY KEY (id),
    CONSTRAINT fk_tool_listings_owner_account FOREIGN KEY (owner_account_id) REFERENCES identity_accounts (id),
    CONSTRAINT fk_tool_listings_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_tool_listings_location FOREIGN KEY (location_id) REFERENCES locations (id)
);

CREATE TABLE tool_images (
    id UUID NOT NULL,
    tool_listing_id UUID NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    display_order INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_tool_images PRIMARY KEY (id),
    CONSTRAINT fk_tool_images_listing FOREIGN KEY (tool_listing_id) REFERENCES tool_listings (id)
);

CREATE INDEX idx_categories_key ON categories (category_key);
CREATE INDEX idx_tool_listings_owner_account ON tool_listings (owner_account_id);
CREATE INDEX idx_tool_listings_category ON tool_listings (category_id);
CREATE INDEX idx_tool_listings_status ON tool_listings (status);
CREATE INDEX idx_tool_listings_location ON tool_listings (location_id);
CREATE INDEX idx_tool_images_listing ON tool_images (tool_listing_id);