-- F1: traducciones del catálogo a pt-BR, fr-FR y ja-JP (es-CO y en-US ya existen desde V7).
INSERT INTO category_translations (category_id, locale, name, description)
SELECT c.id, v.locale, v.name, v.description
FROM categories c
JOIN (VALUES
    ('cleaning',     'pt-BR', 'Limpeza',                  'Limpeza de casas, lojas e escritórios'),
    ('moving',       'pt-BR', 'Mudanças e carga',         'Carga, descarga e transporte de pertences'),
    ('construction', 'pt-BR', 'Construção e obra',        'Ajudante de obra, pequenas demolições e transporte de material'),
    ('painting',     'pt-BR', 'Pintura',                  'Pintura de interiores e exteriores'),
    ('gardening',    'pt-BR', 'Jardinagem',               'Poda e limpeza de jardins e áreas verdes'),
    ('events',       'pt-BR', 'Eventos',                  'Montagem, desmontagem e apoio em eventos'),
    ('warehouse',    'pt-BR', 'Armazém e inventário',     'Organização, embalagem e contagem'),
    ('delivery',     'pt-BR', 'Entregas e recados',       'Entregas e recados locais'),

    ('cleaning',     'fr-FR', 'Ménage et nettoyage',      'Nettoyage de maisons, commerces et bureaux'),
    ('moving',       'fr-FR', 'Déménagement et manutention', 'Chargement, déchargement et transport de biens'),
    ('construction', 'fr-FR', 'Construction et chantier', 'Aide de chantier, petite démolition et transport de matériaux'),
    ('painting',     'fr-FR', 'Peinture',                 'Peinture intérieure et extérieure'),
    ('gardening',    'fr-FR', 'Jardinage',                'Taille et entretien des jardins et espaces verts'),
    ('events',       'fr-FR', 'Événements',               'Montage, démontage et assistance lors d’événements'),
    ('warehouse',    'fr-FR', 'Entrepôt et inventaire',   'Rangement, emballage et comptage'),
    ('delivery',     'fr-FR', 'Courses et livraisons',    'Livraisons et courses locales'),

    ('cleaning',     'ja-JP', '清掃',                     '住宅・店舗・オフィスの清掃'),
    ('moving',       'ja-JP', '引越し・荷物運搬',          '荷物の積み込み・積み下ろし・運搬'),
    ('construction', 'ja-JP', '建設・現場作業',            '現場補助、小規模解体、資材運搬'),
    ('painting',     'ja-JP', '塗装',                     '屋内・屋外の塗装'),
    ('gardening',    'ja-JP', '庭仕事',                   '剪定、庭や緑地の手入れ'),
    ('events',       'ja-JP', 'イベント',                  'イベントの設営・撤去・サポート'),
    ('warehouse',    'ja-JP', '倉庫・棚卸し',              '整理、梱包、数量確認'),
    ('delivery',     'ja-JP', '配達・お使い',              '地域内の配達とお使い')
) AS v(slug, locale, name, description) ON v.slug = c.slug;

-- Precio por categoría y región, en la unidad menor de la moneda (D8: COP 0, BRL/USD/EUR 2, JPY 0).
-- Sin FK hacia region_profiles (otro módulo). Precios de DEMOSTRACIÓN: VERIFICAR antes de usarlos.
CREATE TABLE category_prices (
    category_id  UUID        NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    region_code  VARCHAR(2)  NOT NULL,
    unit         VARCHAR(10) NOT NULL,
    price_minor  BIGINT      NOT NULL CHECK (price_minor >= 0),
    currency     VARCHAR(3)  NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (category_id, region_code)
);

INSERT INTO category_prices (category_id, region_code, unit, price_minor, currency)
SELECT c.id, v.region, CASE WHEN c.slug = 'delivery' THEN 'TRIP' ELSE 'DAY' END, v.price, v.currency
FROM categories c
JOIN (VALUES
    ('cleaning', 'CO', 90000, 'COP'), ('moving', 'CO', 120000, 'COP'), ('construction', 'CO', 100000, 'COP'),
    ('painting', 'CO', 110000, 'COP'), ('gardening', 'CO', 90000, 'COP'), ('events', 'CO', 95000, 'COP'),
    ('warehouse', 'CO', 85000, 'COP'), ('delivery', 'CO', 15000, 'COP'),

    ('cleaning', 'BR', 18000, 'BRL'), ('moving', 'BR', 25000, 'BRL'), ('construction', 'BR', 20000, 'BRL'),
    ('painting', 'BR', 22000, 'BRL'), ('gardening', 'BR', 18000, 'BRL'), ('events', 'BR', 19000, 'BRL'),
    ('warehouse', 'BR', 17000, 'BRL'), ('delivery', 'BR', 3000, 'BRL'),

    ('cleaning', 'US', 16000, 'USD'), ('moving', 'US', 22000, 'USD'), ('construction', 'US', 20000, 'USD'),
    ('painting', 'US', 21000, 'USD'), ('gardening', 'US', 16000, 'USD'), ('events', 'US', 18000, 'USD'),
    ('warehouse', 'US', 15000, 'USD'), ('delivery', 'US', 2500, 'USD'),

    ('cleaning', 'FR', 14000, 'EUR'), ('moving', 'FR', 20000, 'EUR'), ('construction', 'FR', 18000, 'EUR'),
    ('painting', 'FR', 19000, 'EUR'), ('gardening', 'FR', 14000, 'EUR'), ('events', 'FR', 16000, 'EUR'),
    ('warehouse', 'FR', 13000, 'EUR'), ('delivery', 'FR', 2000, 'EUR'),

    ('cleaning', 'JP', 15000, 'JPY'), ('moving', 'JP', 22000, 'JPY'), ('construction', 'JP', 20000, 'JPY'),
    ('painting', 'JP', 21000, 'JPY'), ('gardening', 'JP', 15000, 'JPY'), ('events', 'JP', 17000, 'JPY'),
    ('warehouse', 'JP', 14000, 'JPY'), ('delivery', 'JP', 2000, 'JPY')
) AS v(slug, region, price, currency) ON v.slug = c.slug;
