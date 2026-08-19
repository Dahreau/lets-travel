CREATE TABLE payment_methods (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    provider VARCHAR(20) NOT NULL,
    type VARCHAR(20) NOT NULL,
    provider_token VARCHAR(255) NOT NULL,
    brand VARCHAR(50),
    last4 VARCHAR(4),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    travel_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    payment_method_id UUID REFERENCES payment_methods(id) ON DELETE SET NULL,
    amount NUMERIC(10, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider_reference VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
