package com.jornal.vacancies.translation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contenido de una vacante en el idioma de quien la ve. Si el idioma es distinto al original, se traduce con
 * {@link Translator} una sola vez y se guarda en {@code vacancy_translations}. Si el servicio no responde, se
 * muestra el original y no se vuelve a intentar con ese idioma durante un minuto (la página no se queda esperando).
 */
@Component
public class VacancyTranslations {

    private static final Logger log = LoggerFactory.getLogger(VacancyTranslations.class);

    /** Contenido mostrado: traducido o el original. {@code sourceLanguage}: idioma en que se escribió. */
    public record Content(String title, String description, List<String> items, String sourceLanguage, boolean translated) {
    }

    private final Translator translator;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    /** Guarda la traducción en su propia transacción: la consulta que la pide es de solo lectura. */
    private final TransactionTemplate writer;
    private final Map<String, Instant> pausedUntil = new ConcurrentHashMap<>();

    public VacancyTranslations(Translator translator, JdbcTemplate jdbc, Clock clock, PlatformTransactionManager tx) {
        this.writer = new TransactionTemplate(tx);
        this.writer.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.translator = translator;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public static String language(Locale locale) {
        return locale == null ? "" : locale.getLanguage().toLowerCase(Locale.ROOT);
    }

    public static String language(String tag) {
        return tag == null ? "" : language(Locale.forLanguageTag(tag));
    }

    public Content contentOf(UUID vacancyId, String title, String description, List<String> items, String sourceTag,
                             Locale viewer) {
        String source = language(sourceTag);
        String target = language(viewer);
        Content original = new Content(title, description, List.copyOf(items), source, false);
        if (source.isEmpty() || target.isEmpty() || source.equals(target) || !translator.enabled()) {
            return original;
        }
        List<Map<String, Object>> cached = jdbc.queryForList(
                "SELECT title, description, items FROM vacancy_translations WHERE vacancy_id = ? AND language = ?",
                vacancyId, target);
        if (!cached.isEmpty()) {
            Map<String, Object> row = cached.get(0);
            String joined = (String) row.get("items");
            return new Content((String) row.get("title"), (String) row.get("description"),
                    joined.isEmpty() ? List.of() : List.of(joined.split("\n")), source, true);
        }
        String pair = source + ">" + target;
        Instant now = clock.instant();
        Instant paused = pausedUntil.get(pair);
        if (paused != null && now.isBefore(paused)) return original;
        try {
            List<String> texts = new ArrayList<>(List.of(title, description));
            texts.addAll(items);
            List<String> out = translator.translate(texts, source, target);
            List<String> translatedItems = List.copyOf(out.subList(2, out.size()));
            writer.executeWithoutResult(status -> jdbc.update("""
                    INSERT INTO vacancy_translations (vacancy_id, language, title, description, items, created_at)
                    VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (vacancy_id, language) DO NOTHING
                    """, vacancyId, target, out.get(0), out.get(1), String.join("\n", translatedItems), Timestamp.from(now)));
            return new Content(out.get(0), out.get(1), translatedItems, source, true);
        } catch (RuntimeException e) {
            pausedUntil.put(pair, now.plusSeconds(60));
            log.warn("Translation {} unavailable, showing original: {}", pair, e.getMessage());
            return original;
        }
    }
}
