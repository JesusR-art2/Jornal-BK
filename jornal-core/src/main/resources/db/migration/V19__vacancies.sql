-- Vacantes de trabajo temporal: la empresa publica, los trabajadores cercanos reciben aviso y se postulan,
-- la empresa elige a los que necesita, cada elegido confirma asistencia y, al terminar, la empresa cierra
-- a cada persona como finalizada (pasa a pago) o cancelada con justificación (no se le paga).

CREATE TABLE vacancies (
    id               UUID         PRIMARY KEY,
    employer_id      UUID         NOT NULL,              -- sin FK: módulo users
    employer_role    VARCHAR(20)  NOT NULL,
    region_code      VARCHAR(2)   NOT NULL,
    title            VARCHAR(120) NOT NULL,
    description      VARCHAR(2000) NOT NULL,             -- comentario del empleador: qué hay que hacer
    category_id      UUID,                               -- opcional, sin FK: módulo catalog
    address_text     VARCHAR(400) NOT NULL,
    latitude         DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude        DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    location         GEOGRAPHY(Point, 4326) GENERATED ALWAYS AS
                         (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography) STORED,
    timezone         VARCHAR(40)  NOT NULL,              -- zona IANA del lugar (D9)
    starts_at        TIMESTAMPTZ  NOT NULL,
    ends_at          TIMESTAMPTZ  NOT NULL,
    positions        INTEGER      NOT NULL CHECK (positions BETWEEN 1 AND 200),
    pay_minor        BIGINT       NOT NULL CHECK (pay_minor >= 0),   -- pago por persona (D8)
    currency         VARCHAR(3)   NOT NULL,
    money_scale      INTEGER      NOT NULL,
    status           VARCHAR(20)  NOT NULL,              -- OPEN, FILLED, CLOSED, CANCELLED
    event_seq        BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    CHECK (ends_at > starts_at)
);

CREATE INDEX idx_vacancies_employer ON vacancies (employer_id, created_at DESC);
CREATE INDEX idx_vacancies_status   ON vacancies (status, starts_at);
CREATE INDEX idx_vacancies_location ON vacancies USING GIST (location);

-- Implementos que debe llevar el trabajador
CREATE TABLE vacancy_items (
    vacancy_id  UUID         NOT NULL REFERENCES vacancies (id) ON DELETE CASCADE,
    position    INTEGER      NOT NULL,
    item        VARCHAR(120) NOT NULL,
    PRIMARY KEY (vacancy_id, position)
);

CREATE TABLE vacancy_applications (
    id               UUID         PRIMARY KEY,
    vacancy_id       UUID         NOT NULL REFERENCES vacancies (id) ON DELETE CASCADE,
    worker_id        UUID         NOT NULL,              -- sin FK: módulo users
    status           VARCHAR(20)  NOT NULL,
    message          VARCHAR(500),                       -- nota del trabajador al postularse
    distance_km      DOUBLE PRECISION,
    selected_at      TIMESTAMPTZ,
    responded_at     TIMESTAMPTZ,                        -- confirmó o declinó asistencia
    closed_at        TIMESTAMPTZ,                        -- finalizado o cancelado por el empleador
    close_reason     VARCHAR(30),                        -- NO_SHOW, ABANDONED, OTHER
    justification    VARCHAR(1000),
    payout_minor     BIGINT,
    payout_status    VARCHAR(20),                        -- PENDING, PAID
    paid_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    UNIQUE (vacancy_id, worker_id)
);

CREATE INDEX idx_vacancy_applications_worker ON vacancy_applications (worker_id, created_at DESC);
CREATE INDEX idx_vacancy_applications_payout ON vacancy_applications (payout_status);
