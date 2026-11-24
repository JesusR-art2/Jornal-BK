-- F2: refresh tokens con rotación. Solo se guarda el hash SHA-256; el token en claro lo tiene el cliente.
-- family_id agrupa la cadena de rotaciones: si un token ya rotado se vuelve a usar, se revoca toda la familia.
CREATE TABLE refresh_tokens (
    id           UUID         PRIMARY KEY,
    user_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id    UUID         NOT NULL,
    token_hash   VARCHAR(64)  NOT NULL UNIQUE,
    issued_at    TIMESTAMPTZ  NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    revoked_at   TIMESTAMPTZ,
    replaced_by  UUID
);

CREATE INDEX idx_refresh_tokens_user   ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
