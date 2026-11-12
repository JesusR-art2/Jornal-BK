CREATE TABLE categories (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug           VARCHAR(60) NOT NULL UNIQUE,
    name           VARCHAR(100) NOT NULL,
    description    VARCHAR(255),
    active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO categories (slug, name, description) VALUES
    ('cleaning',     'Aseo y limpieza',         'Limpieza de casas, locales y oficinas'),
    ('moving',       'Mudanzas y cargue',       'Cargue, descargue y traslado de enseres'),
    ('construction', 'Construcción y obra',     'Ayudante de obra, demolición menor, acarreo'),
    ('painting',     'Pintura',                 'Pintura de interiores y exteriores'),
    ('gardening',    'Jardinería',              'Poda, limpieza de jardines y zonas verdes'),
    ('events',       'Eventos',                 'Montaje, desmontaje y apoyo en eventos'),
    ('warehouse',    'Bodega e inventario',     'Organización, empaque y conteo'),
    ('delivery',     'Mensajería y domicilios', 'Entregas y mandados locales');