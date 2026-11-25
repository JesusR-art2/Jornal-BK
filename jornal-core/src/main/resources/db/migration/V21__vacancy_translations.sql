-- Traducción automática del contenido de las vacantes (título, qué hay que hacer e implementos) al idioma de
-- quien las ve. La vacante guarda el idioma en que se escribió; cada traducción se calcula una sola vez.

ALTER TABLE vacancies ADD COLUMN source_locale VARCHAR(10);

CREATE TABLE vacancy_translations (
    vacancy_id   UUID          NOT NULL REFERENCES vacancies (id) ON DELETE CASCADE,
    language     VARCHAR(5)    NOT NULL,              -- idioma de destino (es, en, pt, fr, de, ja)
    title        VARCHAR(400)  NOT NULL,
    description  VARCHAR(6000) NOT NULL,
    items        TEXT          NOT NULL,              -- implementos separados por salto de línea
    created_at   TIMESTAMPTZ   NOT NULL,
    PRIMARY KEY (vacancy_id, language)
);
