-- F1: perfil regional. Todo lo que cambia entre países vive aquí como dato.
-- Los valores normativos (impuestos, leyes, festivos) y comerciales (tarifas) son de REFERENCIA:
-- llevan review_status = 'VERIFICAR' y un campo source hasta que el equipo los confirme con fuentes oficiales.

-- Países: entra Japón; se habilitan BR, US, FR y JP; Italia queda deshabilitada.
INSERT INTO countries (code, name, currency, default_locale, phone_prefix, commission_bps, document_types, enabled)
VALUES ('JP', 'Japón', 'JPY', 'ja-JP', '+81', 1000, 'MY_NUMBER,CORPORATE_NUMBER', TRUE);

UPDATE countries SET enabled = TRUE  WHERE code IN ('BR', 'US', 'FR');
UPDATE countries SET enabled = FALSE WHERE code = 'IT';
UPDATE countries SET document_types = 'CC,CE,PPT,NIT'        WHERE code = 'CO';
UPDATE countries SET document_types = 'CPF,CNPJ'             WHERE code = 'BR';
UPDATE countries SET document_types = 'SSN,PASSPORT,EIN'     WHERE code = 'US';
UPDATE countries SET document_types = 'ID_CARD,PASSPORT,SIRET' WHERE code = 'FR';

-- Perfil regional (sin FK a countries: pertenece a otro módulo; comparten la clave).
CREATE TABLE region_profiles (
    code                 VARCHAR(2)   PRIMARY KEY,
    default_locale       VARCHAR(10)  NOT NULL,
    supported_locales    VARCHAR(100) NOT NULL,
    currency             VARCHAR(3)   NOT NULL,
    money_scale          INTEGER      NOT NULL CHECK (money_scale BETWEEN 0 AND 4),
    default_timezone     VARCHAR(40)  NOT NULL,
    allowed_timezones    VARCHAR(500) NOT NULL,
    expected_grouping    VARCHAR(4)   NOT NULL,
    expected_decimal     VARCHAR(4)   NOT NULL,
    symbol_position      VARCHAR(10)  NOT NULL,
    date_pattern         VARCHAR(20)  NOT NULL,
    hour_cycle           VARCHAR(3)   NOT NULL,
    first_day_of_week    VARCHAR(10)  NOT NULL,
    measurement_system   VARCHAR(10)  NOT NULL,
    phone_prefix         VARCHAR(6)   NOT NULL,
    postal_code_regex    VARCHAR(100) NOT NULL,
    postal_label_key     VARCHAR(40)  NOT NULL,
    name_order           VARCHAR(20)  NOT NULL,
    prices_include_tax   BOOLEAN      NOT NULL,
    receipt_type         VARCHAR(40)  NOT NULL,
    receipt_prefix       VARCHAR(10)  NOT NULL,
    data_protection_law  VARCHAR(120) NOT NULL,
    terms_version        VARCHAR(20)  NOT NULL,
    privacy_version      VARCHAR(20)  NOT NULL,
    tip_mode             VARCHAR(10)  NOT NULL,
    tip_suggested_pcts   VARCHAR(40)  NOT NULL DEFAULT '',
    commission_bps       INTEGER      NOT NULL CHECK (commission_bps BETWEEN 0 AND 10000),
    travel_fee_minor     BIGINT       NOT NULL CHECK (travel_fee_minor >= 0),
    review_status        VARCHAR(12)  NOT NULL DEFAULT 'VERIFICAR',
    source               VARCHAR(255),
    version              BIGINT       NOT NULL DEFAULT 0,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE region_address_fields (
    region_code  VARCHAR(2)   NOT NULL REFERENCES region_profiles (code) ON DELETE CASCADE,
    position     INTEGER      NOT NULL,
    field_key    VARCHAR(40)  NOT NULL,
    label_key    VARCHAR(60)  NOT NULL,
    required     BOOLEAN      NOT NULL,
    regex        VARCHAR(200),
    max_length   INTEGER      NOT NULL DEFAULT 120,
    PRIMARY KEY (region_code, position)
);

CREATE TABLE region_tax_rules (
    region_code    VARCHAR(2)   NOT NULL REFERENCES region_profiles (code) ON DELETE CASCADE,
    position       INTEGER      NOT NULL,
    name           VARCHAR(60)  NOT NULL,
    rate_bps       INTEGER      NOT NULL CHECK (rate_bps BETWEEN 0 AND 10000),
    included       BOOLEAN      NOT NULL,
    source         VARCHAR(255) NOT NULL,
    valid_from     DATE         NOT NULL,
    review_status  VARCHAR(12)  NOT NULL DEFAULT 'VERIFICAR',
    PRIMARY KEY (region_code, position)
);

CREATE TABLE region_payment_methods (
    region_code  VARCHAR(2)  NOT NULL REFERENCES region_profiles (code) ON DELETE CASCADE,
    position     INTEGER     NOT NULL,
    code         VARCHAR(30) NOT NULL,
    label_key    VARCHAR(60) NOT NULL,
    flow         VARCHAR(20) NOT NULL,
    PRIMARY KEY (region_code, position)
);

CREATE TABLE region_document_types (
    region_code  VARCHAR(2)   NOT NULL REFERENCES region_profiles (code) ON DELETE CASCADE,
    position     INTEGER      NOT NULL,
    code         VARCHAR(30)  NOT NULL,
    kind         VARCHAR(10)  NOT NULL,
    validator    VARCHAR(30)  NOT NULL,
    regex        VARCHAR(200),
    PRIMARY KEY (region_code, position)
);

CREATE TABLE region_holidays (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    region_code    VARCHAR(2)   NOT NULL REFERENCES region_profiles (code) ON DELETE CASCADE,
    holiday_date   DATE         NOT NULL,
    name           VARCHAR(120) NOT NULL,
    review_status  VARCHAR(12)  NOT NULL DEFAULT 'VERIFICAR',
    UNIQUE (region_code, holiday_date)
);

-- ---------------------------------------------------------------------------------------------
-- Perfiles (valores de referencia, VERIFICAR)
-- ---------------------------------------------------------------------------------------------
INSERT INTO region_profiles (code, default_locale, supported_locales, currency, money_scale,
    default_timezone, allowed_timezones, expected_grouping, expected_decimal, symbol_position,
    date_pattern, hour_cycle, first_day_of_week, measurement_system, phone_prefix,
    postal_code_regex, postal_label_key, name_order, prices_include_tax, receipt_type, receipt_prefix,
    data_protection_law, terms_version, privacy_version, tip_mode, tip_suggested_pcts,
    commission_bps, travel_fee_minor, source) VALUES
('CO', 'es-CO', 'es-CO,en-US', 'COP', 0,
    'America/Bogota', 'America/Bogota', '.', ',', 'BEFORE',
    'dd/MM/yyyy', 'h12', 'SUNDAY', 'METRIC', '+57',
    '^\d{6}$', 'address.postal_code', 'GIVEN_FAMILY', TRUE, 'DIAN_E_INVOICE', 'FE',
    'Ley 1581 de 2012', '2026-11-13', '2026-11-13', 'OPTIONAL', '10',
    1000, 8000, 'Valores de referencia del equipo; tarifa de desplazamiento de demostración'),
('BR', 'pt-BR', 'pt-BR,en-US', 'BRL', 2,
    'America/Sao_Paulo', 'America/Sao_Paulo,America/Manaus,America/Belem,America/Fortaleza,America/Recife,America/Cuiaba,America/Porto_Velho,America/Rio_Branco,America/Noronha', '.', ',', 'BEFORE',
    'dd/MM/yyyy', 'h23', 'SUNDAY', 'METRIC', '+55',
    '^\d{5}-?\d{3}$', 'address.postal_code_br', 'GIVEN_FAMILY', TRUE, 'NFS_E', 'NFSE',
    'LGPD (Lei 13.709/2018)', '2026-11-13', '2026-11-13', 'OPTIONAL', '10',
    1000, 1000, 'Valores de referencia del equipo; tarifa de desplazamiento de demostración'),
('US', 'en-US', 'en-US', 'USD', 2,
    'America/New_York', 'America/New_York,America/Chicago,America/Denver,America/Phoenix,America/Los_Angeles,America/Anchorage,Pacific/Honolulu', ',', '.', 'BEFORE',
    'MM/dd/yyyy', 'h12', 'SUNDAY', 'US', '+1',
    '^\d{5}(-\d{4})?$', 'address.postal_code_us', 'GIVEN_FAMILY', FALSE, 'SALES_RECEIPT', 'RCPT',
    'CCPA y leyes estatales de privacidad', '2026-11-13', '2026-11-13', 'PROMPT', '15,18,20',
    1000, 1000, 'Valores de referencia del equipo; tarifa de desplazamiento de demostración'),
('FR', 'fr-FR', 'fr-FR,en-US', 'EUR', 2,
    'Europe/Paris', 'Europe/Paris', U&'\202F', ',', 'AFTER',
    'dd/MM/yyyy', 'h23', 'MONDAY', 'METRIC', '+33',
    '^\d{5}$', 'address.postal_code', 'GIVEN_FAMILY', TRUE, 'FACTURE', 'FA',
    'RGPD (Règlement (UE) 2016/679)', '2026-11-13', '2026-11-13', 'OPTIONAL', '5,10',
    1000, 800, 'Valores de referencia del equipo; tarifa de desplazamiento de demostración'),
('JP', 'ja-JP', 'ja-JP,en-US', 'JPY', 0,
    'Asia/Tokyo', 'Asia/Tokyo', ',', '.', 'BEFORE',
    'yyyy/MM/dd', 'h23', 'SUNDAY', 'METRIC', '+81',
    '^\d{3}-?\d{4}$', 'address.postal_code_jp', 'FAMILY_GIVEN', TRUE, 'QUALIFIED_INVOICE', 'INV',
    'APPI (Ley 57 de 2003)', '2026-11-13', '2026-11-13', 'HIDDEN', '',
    1000, 1000, 'Valores de referencia del equipo; tarifa de desplazamiento de demostración');

-- Direcciones: campos ordenados por país.
INSERT INTO region_address_fields (region_code, position, field_key, label_key, required, regex, max_length) VALUES
('CO', 0, 'street_address', 'address.street_address', TRUE,  NULL, 120),
('CO', 1, 'neighborhood',   'address.neighborhood',   FALSE, NULL, 80),
('CO', 2, 'city',           'address.city',           TRUE,  NULL, 80),
('CO', 3, 'department',     'address.department',     TRUE,  NULL, 80),
('CO', 4, 'postal_code',    'address.postal_code',    FALSE, NULL, 10),

('BR', 0, 'street',         'address.street',         TRUE,  NULL, 120),
('BR', 1, 'number',         'address.number',         TRUE,  '^([0-9]{1,6}[A-Za-z]?|S/N)$', 10),
('BR', 2, 'complement',     'address.complement',     FALSE, NULL, 80),
('BR', 3, 'neighborhood',   'address.neighborhood',   TRUE,  NULL, 80),
('BR', 4, 'city',           'address.city',           TRUE,  NULL, 80),
('BR', 5, 'state',          'address.state',          TRUE,  '^[A-Z]{2}$', 2),
('BR', 6, 'postal_code',    'address.postal_code_br', TRUE,  NULL, 9),

('US', 0, 'line1',          'address.line1',          TRUE,  NULL, 120),
('US', 1, 'line2',          'address.line2',          FALSE, NULL, 120),
('US', 2, 'city',           'address.city',           TRUE,  NULL, 80),
('US', 3, 'state',          'address.state',          TRUE,  '^[A-Z]{2}$', 2),
('US', 4, 'postal_code',    'address.postal_code_us', TRUE,  NULL, 10),

('FR', 0, 'street_address', 'address.street_address', TRUE,  NULL, 120),
('FR', 1, 'complement',     'address.complement',     FALSE, NULL, 120),
('FR', 2, 'postal_code',    'address.postal_code',    TRUE,  NULL, 5),
('FR', 3, 'city',           'address.city',           TRUE,  NULL, 80),

('JP', 0, 'postal_code',    'address.postal_code_jp', TRUE,  NULL, 8),
('JP', 1, 'prefecture',     'address.prefecture',     TRUE,  NULL, 20),
('JP', 2, 'city',           'address.city',           TRUE,  NULL, 80),
('JP', 3, 'town',           'address.town',           TRUE,  NULL, 80),
('JP', 4, 'block',          'address.block',          TRUE,  '^[0-9０-９]+([-－][0-9０-９]+){0,3}$', 20),
('JP', 5, 'building',       'address.building',       FALSE, NULL, 120);

-- Impuestos (tasa en puntos básicos: 1900 = 19 %).
INSERT INTO region_tax_rules (region_code, position, name, rate_bps, included, source, valid_from) VALUES
('CO', 0, 'IVA',               1900, TRUE,  'Estatuto Tributario art. 468 (tarifa general)', '2017-01-01'),
('BR', 0, 'ISS',                500, TRUE,  'LC 116/2003: ISS municipal de 2 % a 5 %; se usa 5 % como referencia', '2003-08-01'),
('US', 0, 'Sales tax (NY State)', 400, FALSE, 'Impuesto estatal de NY sin recargos locales; varía por estado y condado', '2005-06-01'),
('FR', 0, 'TVA',               2000, TRUE,  'CGI art. 278 (taux normal)', '2014-01-01'),
('JP', 0, '消費税',             1000, TRUE,  'Consumption Tax Act (tasa estándar)', '2019-10-01');

-- Medios de pago (simulados). flow elige la estrategia de pago.
INSERT INTO region_payment_methods (region_code, position, code, label_key, flow) VALUES
('CO', 0, 'PSE',     'payment.pse',     'CHARGE_REFUND'),
('CO', 1, 'NEQUI',   'payment.nequi',   'CHARGE_REFUND'),
('CO', 2, 'CARD',    'payment.card',    'AUTH_CAPTURE'),
('BR', 0, 'PIX',     'payment.pix',     'CHARGE_REFUND'),
('BR', 1, 'CARD',    'payment.card',    'AUTH_CAPTURE'),
('BR', 2, 'BOLETO',  'payment.boleto',  'ASYNC_VOUCHER'),
('US', 0, 'CARD',    'payment.card',    'AUTH_CAPTURE'),
('US', 1, 'PAYPAL',  'payment.paypal',  'AUTH_CAPTURE'),
('FR', 0, 'CARD',    'payment.card',    'AUTH_CAPTURE'),
('FR', 1, 'PAYPAL',  'payment.paypal',  'AUTH_CAPTURE'),
('JP', 0, 'CARD',    'payment.card',    'AUTH_CAPTURE'),
('JP', 1, 'PAYPAY',  'payment.paypay',  'CHARGE_REFUND'),
('JP', 2, 'KONBINI', 'payment.konbini', 'ASYNC_VOUCHER');

-- Documentos: kind PERSONAL o TAX; validator es el nombre de la estrategia.
INSERT INTO region_document_types (region_code, position, code, kind, validator, regex) VALUES
('CO', 0, 'CC',               'PERSONAL', 'regex',        '^\d{6,10}$'),
('CO', 1, 'CE',               'PERSONAL', 'regex',        '^\d{5,12}$'),
('CO', 2, 'PPT',              'PERSONAL', 'regex',        '^\d{5,12}$'),
('CO', 3, 'NIT',              'TAX',      'nit-dv',       NULL),
('BR', 0, 'CPF',              'PERSONAL', 'cpf',          NULL),
('BR', 1, 'CNPJ',             'TAX',      'cnpj',         NULL),
('US', 0, 'SSN',              'PERSONAL', 'regex',        '^\d{3}-?\d{2}-?\d{4}$'),
('US', 1, 'PASSPORT',         'PERSONAL', 'regex',        '^[A-Z0-9]{6,9}$'),
('US', 2, 'EIN',              'TAX',      'regex',        '^\d{2}-?\d{7}$'),
('FR', 0, 'ID_CARD',          'PERSONAL', 'regex',        '^[A-Z0-9]{9,12}$'),
('FR', 1, 'PASSPORT',         'PERSONAL', 'regex',        '^\d{2}[A-Z]{2}\d{5}$'),
('FR', 2, 'SIRET',            'TAX',      'luhn',         '^\d{14}$'),
('JP', 0, 'MY_NUMBER',        'PERSONAL', 'jp-mynumber',  NULL),
('JP', 1, 'CORPORATE_NUMBER', 'TAX',      'jp-corporate', NULL);

-- Festivos de referencia (noviembre 2026 a mayo 2027).
INSERT INTO region_holidays (region_code, holiday_date, name) VALUES
('CO', '2026-11-02', 'Día de Todos los Santos (trasladado)'),
('CO', '2026-11-16', 'Independencia de Cartagena (trasladado)'),
('CO', '2026-12-08', 'Inmaculada Concepción'),
('CO', '2026-12-25', 'Navidad'),
('CO', '2027-01-01', 'Año Nuevo'),
('BR', '2026-11-15', 'Proclamação da República'),
('BR', '2026-11-20', 'Dia Nacional de Zumbi e da Consciência Negra'),
('BR', '2026-12-25', 'Natal'),
('BR', '2027-01-01', 'Confraternização Universal'),
('US', '2026-11-11', 'Veterans Day'),
('US', '2026-11-26', 'Thanksgiving Day'),
('US', '2026-12-25', 'Christmas Day'),
('US', '2027-01-01', 'New Year''s Day'),
('FR', '2026-11-01', 'Toussaint'),
('FR', '2026-11-11', 'Armistice 1918'),
('FR', '2026-12-25', 'Noël'),
('FR', '2027-01-01', 'Jour de l''an'),
('JP', '2026-11-03', '文化の日'),
('JP', '2026-11-23', '勤労感謝の日'),
('JP', '2027-01-01', '元日'),
('JP', '2027-04-29', '昭和の日 (Semana Dorada)'),
('JP', '2027-05-03', '憲法記念日 (Semana Dorada)'),
('JP', '2027-05-04', 'みどりの日 (Semana Dorada)'),
('JP', '2027-05-05', 'こどもの日 (Semana Dorada)');
