-- F6: asignación. Proyección de trabajadores (alimentada por jornal.worker.updated.v1), búsquedas,
-- ofertas secuenciales y reservas. La posición en vivo no va aquí: va en Redis GEO con TTL.

CREATE TABLE worker_projection (
    region          VARCHAR(2)   NOT NULL,
    worker_id       UUID         NOT NULL,
    name            VARCHAR(160),
    eligible        BOOLEAN      NOT NULL,
    available       BOOLEAN      NOT NULL,
    category_ids    TEXT         NOT NULL DEFAULT '',   -- UUID separados por comas
    radius_km       INTEGER      NOT NULL,
    base_latitude   DOUBLE PRECISION,
    base_longitude  DOUBLE PRECISION,
    rating_avg      NUMERIC(3,2) NOT NULL DEFAULT 0,
    rating_count    INTEGER      NOT NULL DEFAULT 0,
    version         BIGINT       NOT NULL,              -- solo se aplican versiones más nuevas
    updated_at      TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (region, worker_id)
);

-- Una búsqueda por pedido
CREATE TABLE assignments (
    request_id      UUID         PRIMARY KEY,
    region          VARCHAR(2)   NOT NULL,
    client_id       UUID         NOT NULL,
    category_id     UUID         NOT NULL,
    category_name   VARCHAR(100),
    address_text    VARCHAR(400),
    latitude        DOUBLE PRECISION NOT NULL,
    longitude       DOUBLE PRECISION NOT NULL,
    scheduled_at    TIMESTAMPTZ  NOT NULL,
    status          VARCHAR(20)  NOT NULL,              -- SEARCHING, OFFERED, ASSIGNED, FAILED, CANCELLED, DONE
    worker_id       UUID,
    offers_made     INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_assignments_status ON assignments (status, updated_at);

-- Ofertas: una pendiente por pedido y por trabajador a la vez
CREATE TABLE offers (
    id              UUID         PRIMARY KEY,
    region          VARCHAR(2)   NOT NULL,
    request_id      UUID         NOT NULL REFERENCES assignments (request_id),
    worker_id       UUID         NOT NULL,
    status          VARCHAR(20)  NOT NULL,              -- PENDING, ACCEPTED, REJECTED, EXPIRED, CANCELLED
    distance_km     DOUBLE PRECISION NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    responded_at    TIMESTAMPTZ
);

CREATE INDEX idx_offers_pending ON offers (status, expires_at);
CREATE INDEX idx_offers_worker  ON offers (worker_id, status);
CREATE INDEX idx_offers_request ON offers (request_id);
CREATE UNIQUE INDEX uq_offers_pending_worker  ON offers (worker_id)  WHERE status = 'PENDING';
CREATE UNIQUE INDEX uq_offers_pending_request ON offers (request_id) WHERE status = 'PENDING';

-- Reserva atómica: un trabajador, un pedido; un pedido, un trabajador. Solo una inserción gana.
CREATE TABLE reservations (
    worker_id       UUID         PRIMARY KEY,
    region          VARCHAR(2)   NOT NULL,
    request_id      UUID         NOT NULL UNIQUE,
    reserved_at     TIMESTAMPTZ  NOT NULL
);
