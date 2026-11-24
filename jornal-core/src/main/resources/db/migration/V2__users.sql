CREATE TABLE users (
    id                  UUID PRIMARY KEY,
    email               VARCHAR(255) NOT NULL UNIQUE,
    password_hash       VARCHAR(100) NOT NULL,
    name                VARCHAR(120) NOT NULL,
    phone               VARCHAR(30),
    rol                 VARCHAR(20) NOT NULL,
    verification_state  VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    active              BOOLEAN  NOT NULL DEFAULT TRUE,
    created_in          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_in          TIMESTAMPTZ NOT NULL DEFAULT now()
);