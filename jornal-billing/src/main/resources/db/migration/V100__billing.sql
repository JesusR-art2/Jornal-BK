-- F8: comprobantes por país, numeración consecutiva por región y tipo, y tasas de cambio.

-- Último número emitido por región y tipo de comprobante. Se incrementa dentro de la transacción de la
-- emisión: si la emisión falla, el número no se consume (numeración sin huecos).
CREATE TABLE receipt_sequences (
    region        VARCHAR(2)  NOT NULL,
    receipt_type  VARCHAR(40) NOT NULL,
    last_number   BIGINT      NOT NULL,
    PRIMARY KEY (region, receipt_type)
);

-- Comprobantes particionados por región (LIST); un sexto país cae en la partición por defecto.
CREATE TABLE receipts (
    id                     UUID         NOT NULL,
    region                 VARCHAR(2)   NOT NULL,
    request_id             UUID         NOT NULL,
    receipt_type           VARCHAR(40)  NOT NULL,
    sequence               BIGINT       NOT NULL,
    number                 VARCHAR(40)  NOT NULL,
    client_id              UUID         NOT NULL,
    worker_id              UUID,
    issuer_name            VARCHAR(160) NOT NULL,
    issuer_tax_id          VARCHAR(80)  NOT NULL,
    customer_name          VARCHAR(200),
    customer_legal_name    VARCHAR(200),
    customer_tax_id_type   VARCHAR(20),
    customer_tax_id_number VARCHAR(40),
    locale                 VARCHAR(10)  NOT NULL,
    timezone               VARCHAR(40)  NOT NULL,
    issued_at              TIMESTAMPTZ  NOT NULL,     -- instante UTC
    issued_local           VARCHAR(30)  NOT NULL,     -- fecha y hora en la zona del lugar del trabajo
    currency               VARCHAR(3)   NOT NULL,
    money_scale            INTEGER      NOT NULL,
    subtotal_minor         BIGINT       NOT NULL,
    travel_fee_minor       BIGINT       NOT NULL,
    tax_minor              BIGINT       NOT NULL,
    tax_included           BOOLEAN      NOT NULL,
    tip_minor              BIGINT       NOT NULL,
    total_minor            BIGINT       NOT NULL,
    platform_revenue_minor BIGINT       NOT NULL,     -- comisión + desplazamiento + impuesto sumado
    category_name          VARCHAR(200),
    address_text           VARCHAR(400),
    lines                  TEXT         NOT NULL,     -- JSON: servicio, desplazamiento, impuestos (con tasa) y propina
    PRIMARY KEY (region, id),
    UNIQUE (region, request_id),
    UNIQUE (region, receipt_type, sequence)
) PARTITION BY LIST (region);

CREATE TABLE receipts_co PARTITION OF receipts FOR VALUES IN ('co');
CREATE TABLE receipts_br PARTITION OF receipts FOR VALUES IN ('br');
CREATE TABLE receipts_us PARTITION OF receipts FOR VALUES IN ('us');
CREATE TABLE receipts_fr PARTITION OF receipts FOR VALUES IN ('fr');
CREATE TABLE receipts_jp PARTITION OF receipts FOR VALUES IN ('jp');
CREATE TABLE receipts_other PARTITION OF receipts DEFAULT;

CREATE INDEX idx_receipts_request ON receipts (request_id);
CREATE INDEX idx_receipts_issued ON receipts (issued_at);

-- Tasas de cambio con par, tasa, fuente y fecha. Solo se usan para reportes: los cobros van en moneda local.
CREATE TABLE exchange_rates (
    id          BIGSERIAL     PRIMARY KEY,
    base        VARCHAR(3)    NOT NULL,
    quote       VARCHAR(3)    NOT NULL,
    rate        NUMERIC(24,10) NOT NULL CHECK (rate > 0),   -- 1 base = rate quote
    source      VARCHAR(160)  NOT NULL,
    as_of       DATE          NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_exchange_rate UNIQUE (base, quote, as_of, source)
);
CREATE INDEX idx_exchange_rates_pair ON exchange_rates (base, quote, as_of DESC);
