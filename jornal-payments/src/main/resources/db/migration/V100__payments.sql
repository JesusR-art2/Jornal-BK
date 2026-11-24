-- F5: pagos y libro de movimientos, particionados por región (LIST). Un sexto país cae en la partición
-- por defecto sin cambiar código; si crece, se le crea su partición.

CREATE TABLE payments (
    id                  UUID         NOT NULL,
    region              VARCHAR(2)   NOT NULL,
    request_id          UUID         NOT NULL,
    attempt             INTEGER      NOT NULL,
    client_id           UUID         NOT NULL,
    worker_id           UUID,
    method              VARCHAR(30)  NOT NULL,
    flow                VARCHAR(20)  NOT NULL,
    currency            VARCHAR(3)   NOT NULL,
    amount_minor        BIGINT       NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    provider_ref        VARCHAR(80),
    voucher_code        VARCHAR(40),
    voucher_expires_at  TIMESTAMPTZ,
    failure_reason      VARCHAR(80),
    request_data        TEXT         NOT NULL,     -- datos del pedido (para captura y facturación)
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (region, id),
    UNIQUE (region, request_id, attempt)           -- un cobro por pedido e intento (idempotencia)
) PARTITION BY LIST (region);

CREATE TABLE payments_co PARTITION OF payments FOR VALUES IN ('co');
CREATE TABLE payments_br PARTITION OF payments FOR VALUES IN ('br');
CREATE TABLE payments_us PARTITION OF payments FOR VALUES IN ('us');
CREATE TABLE payments_fr PARTITION OF payments FOR VALUES IN ('fr');
CREATE TABLE payments_jp PARTITION OF payments FOR VALUES IN ('jp');
CREATE TABLE payments_default PARTITION OF payments DEFAULT;

CREATE INDEX idx_payments_request ON payments (region, request_id);
CREATE INDEX idx_payments_voucher ON payments (status, voucher_expires_at);

-- Libro de movimientos: cobros, capturas, reembolsos y liquidación (comisión, pago al trabajador, propina)
CREATE TABLE ledger_entries (
    id            UUID         NOT NULL,
    region        VARCHAR(2)   NOT NULL,
    request_id    UUID         NOT NULL,
    payment_id    UUID         NOT NULL,
    kind          VARCHAR(20)  NOT NULL,
    amount_minor  BIGINT       NOT NULL,           -- negativo en reembolsos
    currency      VARCHAR(3)   NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (region, id)
) PARTITION BY LIST (region);

CREATE TABLE ledger_entries_co PARTITION OF ledger_entries FOR VALUES IN ('co');
CREATE TABLE ledger_entries_br PARTITION OF ledger_entries FOR VALUES IN ('br');
CREATE TABLE ledger_entries_us PARTITION OF ledger_entries FOR VALUES IN ('us');
CREATE TABLE ledger_entries_fr PARTITION OF ledger_entries FOR VALUES IN ('fr');
CREATE TABLE ledger_entries_jp PARTITION OF ledger_entries FOR VALUES IN ('jp');
CREATE TABLE ledger_entries_default PARTITION OF ledger_entries DEFAULT;

CREATE INDEX idx_ledger_request ON ledger_entries (region, request_id);
