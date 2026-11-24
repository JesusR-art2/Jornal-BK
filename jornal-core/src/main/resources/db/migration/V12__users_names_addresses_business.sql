-- F1: nombre en partes, direcciones validadas por país y perfil empresarial (rol BUSINESS, D2).

ALTER TABLE users
    ADD COLUMN given_name  VARCHAR(80),
    ADD COLUMN family_name VARCHAR(80);

-- Relleno desde name: primera palabra como nombre, el resto como apellido.
UPDATE users
SET given_name  = split_part(trim(name), ' ', 1),
    family_name = NULLIF(trim(substr(trim(name), length(split_part(trim(name), ' ', 1)) + 1)), '');

ALTER TABLE users ALTER COLUMN given_name SET NOT NULL;

-- Sin valores por defecto ligados a un país: el servicio siempre los asigna desde el perfil regional.
ALTER TABLE users
    ALTER COLUMN country_code DROP DEFAULT,
    ALTER COLUMN locale       DROP DEFAULT;

CREATE TABLE addresses (
    id            UUID         PRIMARY KEY,
    user_id       UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    label         VARCHAR(40),
    country_code  VARCHAR(2)   NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_addresses_user ON addresses (user_id);

CREATE TABLE address_fields (
    address_id  UUID         NOT NULL REFERENCES addresses (id) ON DELETE CASCADE,
    field_key   VARCHAR(40)  NOT NULL,
    value       VARCHAR(200) NOT NULL,
    PRIMARY KEY (address_id, field_key)
);

CREATE TABLE business_profiles (
    user_id             UUID         PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    legal_name          VARCHAR(160) NOT NULL,
    tax_id_type         VARCHAR(30)  NOT NULL,
    tax_id_number       VARCHAR(30)  NOT NULL,
    billing_address_id  UUID         NOT NULL REFERENCES addresses (id),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (tax_id_type, tax_id_number)
);
