CREATE TABLE countries (
    code            VARCHAR(2)   PRIMARY KEY,
    name            VARCHAR(80)  NOT NULL,
    currency        VARCHAR(3)   NOT NULL,
    default_locale  VARCHAR(10)  NOT NULL,
    phone_prefix    VARCHAR(6)   NOT NULL,
    commission_bps  INTEGER      NOT NULL DEFAULT 0,
    document_types  VARCHAR(200) NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT FALSE
);

INSERT INTO countries(code, name, currency, default_locale, phone_prefix, commission_bps, document_types, enabled) VALUES
    ('CO','Colombia', 'COP',es-CO, '+57', 1000, 'CC,CE,PPT', TRUE),
    ('BR', 'Brasil',        'BRL', 'pt-BR', '+55', 1000, 'CPF',             FALSE),
    ('US', 'Estados Unidos','USD', 'en-US', '+1',  1000, 'SSN,PASSPORT',    FALSE),
    ('FR', 'Francia',       'EUR', 'fr-FR', '+33', 1000, 'ID_CARD,PASSPORT',FALSE),
    ('IT', 'Italia',        'EUR', 'it-IT', '+39', 1000, 'CODICE_FISCALE',  FALSE);