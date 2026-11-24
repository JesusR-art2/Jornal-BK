-- Calificaciones en ambos sentidos, solo sobre pedidos COMPLETED: una por pedido y sentido.
CREATE TABLE request_ratings (
    id          UUID PRIMARY KEY,
    request_id  UUID         NOT NULL,
    direction   VARCHAR(20)  NOT NULL,   -- CLIENT_TO_WORKER, WORKER_TO_CLIENT
    rater_id    UUID         NOT NULL,
    ratee_id    UUID         NOT NULL,
    score       INTEGER      NOT NULL CHECK (score BETWEEN 1 AND 5),
    comment     VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_request_rating UNIQUE (request_id, direction)
);
CREATE INDEX idx_request_ratings_ratee ON request_ratings (ratee_id);
