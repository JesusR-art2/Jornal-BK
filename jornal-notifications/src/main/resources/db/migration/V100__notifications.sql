-- Participantes de cada pedido (para permitir la suscripción al tema del pedido en el WebSocket).
CREATE TABLE request_participants (
    request_id  UUID PRIMARY KEY,
    region      VARCHAR(2)  NOT NULL,
    client_id   UUID        NOT NULL,
    worker_id   UUID,
    status      VARCHAR(30) NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);

-- Registro de entregas: una fila por evento, destinatario y canal (idempotente).
-- Las de canal IN_APP son además la bandeja de la app.
CREATE TABLE notifications (
    id           UUID PRIMARY KEY,
    region       VARCHAR(2)   NOT NULL,
    event_id     VARCHAR(64)  NOT NULL,
    request_id   UUID,
    recipient_id UUID         NOT NULL,
    channel      VARCHAR(10)  NOT NULL,     -- IN_APP, EMAIL, SMS, PUSH
    template     VARCHAR(60)  NOT NULL,
    locale       VARCHAR(10)  NOT NULL,
    timezone     VARCHAR(40)  NOT NULL,
    destination  VARCHAR(200),
    subject      TEXT,
    body         TEXT         NOT NULL,
    status       VARCHAR(10)  NOT NULL,     -- SENT, FAILED, SKIPPED
    error        TEXT,
    created_at   TIMESTAMPTZ  NOT NULL,
    read_at      TIMESTAMPTZ,
    CONSTRAINT uq_notification UNIQUE (event_id, recipient_id, channel)
);
CREATE INDEX idx_notifications_recipient ON notifications (recipient_id, created_at DESC);
CREATE INDEX idx_notifications_request ON notifications (request_id);
