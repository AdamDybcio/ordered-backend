CREATE TABLE addresses (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES users(id),
    label             VARCHAR(50) NOT NULL,
    recipient_name    VARCHAR(255) NOT NULL,
    phone             VARCHAR(30),
    street            VARCHAR(255) NOT NULL,
    building_number   VARCHAR(20) NOT NULL,
    apartment_number  VARCHAR(20),
    city              VARCHAR(100) NOT NULL,
    postal_code       VARCHAR(20) NOT NULL,
    country           VARCHAR(2) NOT NULL DEFAULT 'PL',
    is_default        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP NOT NULL
);

CREATE INDEX idx_addresses_user_id ON addresses(user_id);