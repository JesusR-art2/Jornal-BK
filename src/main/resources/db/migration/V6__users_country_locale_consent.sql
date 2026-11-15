ALTER TABLE users
    ADD COLUMN country_code         VARCHAR(2)  NOT NULL DEFAULT 'CO' REFERENCES countries(code),
    ADD COLUMN locale               VARCHAR(10) NOT NULL DEFAULT 'es-CO',
    ADD COLUMN terms_version        VARCHAR(20),
    ADD COLUMN terms_accepted_at    TIMESTAMPTZ,
    ADD COLUMN privacy_version      VARCHAR(20),
    ADD COLUMN privacy_accepted_at  TIMESTAMPTZ;