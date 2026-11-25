-- Pagos automáticos al trabajador de una vacante (transferencia simulada). Uno por postulación (idempotente).
CREATE TABLE payouts (
    id              UUID         NOT NULL,
    region          VARCHAR(2)   NOT NULL,
    application_id  UUID         NOT NULL,
    vacancy_id      UUID         NOT NULL,
    worker_id       UUID         NOT NULL,
    employer_id     UUID         NOT NULL,
    amount_minor    BIGINT       NOT NULL,
    currency        VARCHAR(3)   NOT NULL,
    status          VARCHAR(10)  NOT NULL,     -- PAID, FAILED
    provider_ref    VARCHAR(80),
    failure_reason  VARCHAR(80),
    created_at      TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (region, id),
    UNIQUE (region, application_id)
) PARTITION BY LIST (region);

CREATE TABLE payouts_co PARTITION OF payouts FOR VALUES IN ('co');
CREATE TABLE payouts_br PARTITION OF payouts FOR VALUES IN ('br');
CREATE TABLE payouts_us PARTITION OF payouts FOR VALUES IN ('us');
CREATE TABLE payouts_fr PARTITION OF payouts FOR VALUES IN ('fr');
CREATE TABLE payouts_jp PARTITION OF payouts FOR VALUES IN ('jp');
CREATE TABLE payouts_default PARTITION OF payouts DEFAULT;
