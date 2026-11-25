-- P2: sexto país solo con datos (Alemania, de-DE, EUR). No hay código nuevo por país: el perfil regional elige
-- formatos, impuestos, comprobante, medios de pago y propina, y el catálogo se traduce en la base. Los textos que
-- no estén traducidos al alemán (errores, avisos, comprobante) salen del respaldo en-US.
-- Valores de referencia del equipo: VERIFICAR con fuentes oficiales antes de usarlos.

INSERT INTO countries (code, name, currency, default_locale, phone_prefix, commission_bps, document_types, enabled)
VALUES ('DE', 'Alemania', 'EUR', 'de-DE', '+49', 1000, 'ID_CARD,PASSPORT,USTID', TRUE);

INSERT INTO region_profiles (code, default_locale, supported_locales, currency, money_scale,
    default_timezone, allowed_timezones, expected_grouping, expected_decimal, symbol_position,
    date_pattern, hour_cycle, first_day_of_week, measurement_system, phone_prefix,
    postal_code_regex, postal_label_key, name_order, prices_include_tax, receipt_type, receipt_prefix,
    data_protection_law, terms_version, privacy_version, tip_mode, tip_suggested_pcts,
    commission_bps, travel_fee_minor, source) VALUES
('DE', 'de-DE', 'de-DE,en-US', 'EUR', 2,
    'Europe/Berlin', 'Europe/Berlin', '.', ',', 'AFTER',
    'dd.MM.yyyy', 'h23', 'MONDAY', 'METRIC', '+49',
    '^\d{5}$', 'address.postal_code', 'GIVEN_FAMILY', TRUE, 'RECHNUNG', 'RE',
    'DSGVO (Verordnung (EU) 2016/679) y BDSG', '2026-11-24', '2026-11-24', 'OPTIONAL', '5,10',
    1000, 900, 'Valores de referencia del equipo; tarifa de desplazamiento de demostración');

INSERT INTO region_address_fields (region_code, position, field_key, label_key, required, regex, max_length) VALUES
('DE', 0, 'street_address', 'address.street_address', TRUE,  NULL, 120),
('DE', 1, 'complement',     'address.complement',     FALSE, NULL, 120),
('DE', 2, 'postal_code',    'address.postal_code',    TRUE,  NULL, 5),
('DE', 3, 'city',           'address.city',           TRUE,  NULL, 80);

INSERT INTO region_tax_rules (region_code, position, name, rate_bps, included, source, valid_from) VALUES
('DE', 0, 'USt', 1900, TRUE, 'UStG § 12 Abs. 1 (Regelsteuersatz)', '2007-01-01');

INSERT INTO region_payment_methods (region_code, position, code, label_key, flow) VALUES
('DE', 0, 'CARD',   'payment.card',   'AUTH_CAPTURE'),
('DE', 1, 'PAYPAL', 'payment.paypal', 'AUTH_CAPTURE'),
('DE', 2, 'SEPA',   'payment.sepa',   'CHARGE_REFUND');

-- USt-IdNr: prefijo del país + 9 dígitos (sin el dígito de control ISO 7064; VERIFICAR)
INSERT INTO region_document_types (region_code, position, code, kind, validator, regex) VALUES
('DE', 0, 'ID_CARD',  'PERSONAL', 'regex', '^[CFGHJKLMNPRTVWXYZ0-9]{9}$'),
('DE', 1, 'PASSPORT', 'PERSONAL', 'regex', '^[CFGHJKLMNPRTVWXYZ0-9]{9}$'),
('DE', 2, 'USTID',    'TAX',      'regex', '^DE\d{9}$');

-- Feriados nacionales de 2026 (los de cada estado federado no se cargan)
INSERT INTO region_holidays (region_code, holiday_date, name) VALUES
('DE', '2026-01-01', 'Neujahr'),
('DE', '2026-04-03', 'Karfreitag'),
('DE', '2026-04-06', 'Ostermontag'),
('DE', '2026-05-01', 'Tag der Arbeit'),
('DE', '2026-05-14', 'Christi Himmelfahrt'),
('DE', '2026-05-25', 'Pfingstmontag'),
('DE', '2026-10-03', 'Tag der Deutschen Einheit'),
('DE', '2026-12-25', '1. Weihnachtstag'),
('DE', '2026-12-26', '2. Weihnachtstag');

INSERT INTO category_translations (category_id, locale, name, description)
SELECT c.id, 'de-DE', v.name, v.description
FROM categories c
JOIN (VALUES
    ('cleaning',     'Reinigung',                  'Reinigung von Wohnungen, Geschäften und Büros'),
    ('moving',       'Umzug und Transport',        'Be- und Entladen sowie Transport von Hausrat'),
    ('construction', 'Bau und Renovierung',        'Bauhelfer, kleine Abbrucharbeiten und Materialtransport'),
    ('painting',     'Malerarbeiten',              'Innen- und Außenanstrich'),
    ('gardening',    'Gartenarbeit',               'Rasenmähen, Heckenschnitt und Gartenpflege'),
    ('events',       'Veranstaltungen',            'Auf- und Abbau, Service und Logistik bei Veranstaltungen'),
    ('warehouse',    'Lager und Inventur',         'Sortieren, Verpacken und Inventur'),
    ('delivery',     'Lieferungen und Botengänge', 'Lokale Lieferungen und Besorgungen')
) AS v(slug, name, description) ON v.slug = c.slug;

-- Precios de DEMOSTRACIÓN en céntimos de euro: VERIFICAR
INSERT INTO category_prices (category_id, region_code, unit, price_minor, currency)
SELECT c.id, 'DE', CASE WHEN c.slug = 'delivery' THEN 'TRIP' ELSE 'DAY' END, v.price, 'EUR'
FROM categories c
JOIN (VALUES
    ('cleaning', 15000), ('moving', 21000), ('construction', 19000), ('painting', 20000),
    ('gardening', 15000), ('events', 17000), ('warehouse', 14000), ('delivery', 2200)
) AS v(slug, price) ON v.slug = c.slug;
