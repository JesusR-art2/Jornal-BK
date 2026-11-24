-- F4: pedidos (solicitudes de trabajo), su historial de estados, inbox del core y candados de tareas.

CREATE TABLE service_requests (
    id                 UUID         PRIMARY KEY,
    client_id          UUID         NOT NULL,              -- sin FK: módulo users
    client_role        VARCHAR(20)  NOT NULL,
    region_code        VARCHAR(2)   NOT NULL,
    category_id        UUID         NOT NULL,              -- sin FK: módulo catalog
    category_slug      VARCHAR(60)  NOT NULL,
    category_name      VARCHAR(100) NOT NULL,              -- copia en el idioma del cliente al crear
    unit               VARCHAR(10)  NOT NULL,
    units              INTEGER      NOT NULL CHECK (units BETWEEN 1 AND 30),
    unit_price_minor   BIGINT       NOT NULL,
    currency           VARCHAR(3)   NOT NULL,
    money_scale        INTEGER      NOT NULL,
    subtotal_minor     BIGINT       NOT NULL,
    travel_fee_minor   BIGINT       NOT NULL,
    tax_minor          BIGINT       NOT NULL,
    tax_included       BOOLEAN      NOT NULL,
    tip_minor          BIGINT       NOT NULL,
    total_minor        BIGINT       NOT NULL,
    commission_bps     INTEGER      NOT NULL,
    payment_method     VARCHAR(30)  NOT NULL,
    payment_flow       VARCHAR(20)  NOT NULL,
    payment_attempt    INTEGER      NOT NULL DEFAULT 1,
    payment_captured   BOOLEAN      NOT NULL DEFAULT FALSE,
    receipt_number     VARCHAR(40),
    scheduled_at       TIMESTAMPTZ  NOT NULL,              -- instante UTC (D9)
    timezone           VARCHAR(40)  NOT NULL,              -- zona IANA del lugar del trabajo
    address_text       VARCHAR(400) NOT NULL,
    latitude           DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude          DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    location           GEOGRAPHY(Point, 4326) GENERATED ALWAYS AS
                           (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography) STORED,
    notes              VARCHAR(500),
    status             VARCHAR(30)  NOT NULL,
    worker_id          UUID,
    cancel_reason      VARCHAR(60),
    idempotency_key    VARCHAR(100) NOT NULL,
    event_seq          BIGINT       NOT NULL DEFAULT 0,
    finished_at        TIMESTAMPTZ,
    confirmed_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    version            BIGINT       NOT NULL DEFAULT 0,
    UNIQUE (client_id, idempotency_key)
);

CREATE INDEX idx_requests_client  ON service_requests (client_id, created_at DESC);
CREATE INDEX idx_requests_worker  ON service_requests (worker_id, created_at DESC);
CREATE INDEX idx_requests_status  ON service_requests (status, updated_at);
CREATE INDEX idx_requests_location ON service_requests USING GIST (location);

-- Desglose del precio (servicio, desplazamiento, impuesto, propina)
CREATE TABLE request_lines (
    request_id         UUID         NOT NULL REFERENCES service_requests (id) ON DELETE CASCADE,
    position           INTEGER      NOT NULL,
    kind               VARCHAR(20)  NOT NULL,
    description        VARCHAR(160) NOT NULL,
    quantity           INTEGER      NOT NULL,
    unit_amount_minor  BIGINT       NOT NULL,
    amount_minor       BIGINT       NOT NULL,
    included_in_total  BOOLEAN      NOT NULL,
    PRIMARY KEY (request_id, position)
);

CREATE TABLE request_status_history (
    id           UUID         PRIMARY KEY,
    request_id   UUID         NOT NULL REFERENCES service_requests (id) ON DELETE CASCADE,
    from_status  VARCHAR(30),
    to_status    VARCHAR(30)  NOT NULL,
    at           TIMESTAMPTZ  NOT NULL,
    actor        VARCHAR(60)  NOT NULL,                   -- CLIENT:<id>, WORKER:<id>, ADMIN:<id>, SYSTEM, payments...
    reason       VARCHAR(120)
);

CREATE INDEX idx_request_history ON request_status_history (request_id, at);

-- Inbox del core: mensajes de los subsistemas ya procesados
CREATE TABLE inbox (
    message_id   VARCHAR(64)  PRIMARY KEY,
    type         VARCHAR(120) NOT NULL,
    region       VARCHAR(2)   NOT NULL,
    source       VARCHAR(60)  NOT NULL,
    received_at  TIMESTAMPTZ  NOT NULL
);

-- ShedLock: una sola instancia ejecuta cada tarea programada
CREATE TABLE shedlock (
    name        VARCHAR(64)  PRIMARY KEY,
    lock_until  TIMESTAMP    NOT NULL,
    locked_at   TIMESTAMP    NOT NULL,
    locked_by   VARCHAR(255) NOT NULL
);
