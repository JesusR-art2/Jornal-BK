CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE TABLE category_translations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id     UUID         NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    locale          VARCHAR(10)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(500),
    UNIQUE (category_id, locale)
);

INSERT INTO category_translations (category_id, locale, name, description)
SELECT id, 'es-CO', name, description FROM categories;

INSERT INTO category_translations (category_id, locale, name, description)
SELECT c.id, 'en-US', v.name, V.description
FROM categories c
JOIN (VALUES
          ('cleaning',     'Cleaning',                 'Cleaning of homes, shops and offices'),
          ('moving',       'Moving and loading',       'Loading, unloading and moving of belongings'),
          ('construction', 'Construction and site work','Site helper, minor demolition, hauling'),
          ('painting',     'Painting',                 'Interior and exterior painting'),
          ('gardening',    'Gardening',                'Pruning, garden and green area cleanup'),
          ('events',       'Events',                   'Setup, teardown and support at events'),
          ('warehouse',    'Warehouse and inventory',  'Organizing, packing and counting'),
          ('delivery',     'Errands and deliveries',   'Local deliveries and errands')
          ) AS v(slug, name, description) ON v.slug = c.slug;