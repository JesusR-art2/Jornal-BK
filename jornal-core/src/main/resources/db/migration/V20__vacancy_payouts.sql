-- Pago al trabajador de una vacante: automático (lo hace el subsistema de pagos al finalizar) o a mano
-- (el empleador registra cuánto pagó; si paga menos de lo acordado debe justificarlo).

-- Configuración del empleador (botón activo/inactivo en Configuración)
CREATE TABLE employer_settings (
    employer_id  UUID         PRIMARY KEY,               -- sin FK: módulo users
    auto_payout  BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at   TIMESTAMPTZ  NOT NULL
);

ALTER TABLE vacancy_applications
    ADD COLUMN payout_mode        VARCHAR(10),            -- AUTO o MANUAL, fijado al finalizar
    ADD COLUMN paid_amount_minor  BIGINT CHECK (paid_amount_minor >= 0),
    ADD COLUMN payment_note       VARCHAR(1000),          -- justificación si se pagó menos de lo acordado
    ADD COLUMN payout_ref         VARCHAR(80),            -- referencia del proveedor (pago automático)
    ADD COLUMN payout_failure     VARCHAR(120);
