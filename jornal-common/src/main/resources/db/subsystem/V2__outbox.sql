-- Outbox de los subsistemas: los mensajes salientes se guardan en la misma transacción del cambio
-- y OutboxRelay los envía a RabbitMQ. Si el broker está caído, esperan aquí.
CREATE TABLE outbox (
    id           UUID         PRIMARY KEY,
    routing_key  VARCHAR(200) NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    sent_at      TIMESTAMPTZ
);

CREATE INDEX idx_outbox_pending ON outbox (created_at) WHERE sent_at IS NULL;
