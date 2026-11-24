-- Común a los subsistemas. Sus migraciones propias empiezan en V100 (classpath:db/migration).

-- Inbox: mensajes ya procesados (idempotencia ante reenvíos y reintentos).
CREATE TABLE inbox (
    message_id   VARCHAR(64)  PRIMARY KEY,
    type         VARCHAR(120) NOT NULL,
    region       VARCHAR(2)   NOT NULL,
    source       VARCHAR(60)  NOT NULL,
    received_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_inbox_region ON inbox (region);

-- Copia del perfil regional publicado por cada core (solo versiones más nuevas).
CREATE TABLE region_profile_cache (
    region      VARCHAR(2)  PRIMARY KEY,
    version     BIGINT      NOT NULL,
    payload     TEXT        NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);
