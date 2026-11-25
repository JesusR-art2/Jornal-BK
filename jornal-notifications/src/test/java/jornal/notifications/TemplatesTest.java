package jornal.notifications;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Plantillas en los 5 idiomas: plurales CLDR, números por idioma, respaldo y hora en la zona del destinatario. */
class TemplatesTest {

    private final Templates templates = new Templates(messageSource());

    private static ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("templates/notifications");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

    private static Map<String, Object> offer(long seconds) {
        Map<String, Object> args = new HashMap<>();
        args.put("category", "Bodega");
        args.put("address", "Calle 26");
        args.put("distance", 1.5);
        args.put("seconds", seconds);
        args.put("when", "mañana");
        return args;
    }

    @Test
    void pluralsFollowEachLanguage() {
        assertThat(body("es-CO", 1)).contains("1 segundo para aceptar").contains("1,5 km");
        assertThat(body("es-CO", 45)).contains("45 segundos para aceptar");
        assertThat(body("en-US", 1)).contains("1 second to accept").contains("1.5 km");
        assertThat(body("en-US", 45)).contains("45 seconds to accept");
        assertThat(body("pt-BR", 45)).contains("45 segundos para aceitar").contains("1,5 km");
        assertThat(body("fr-FR", 1)).contains("1 seconde pour accepter");
        assertThat(body("fr-FR", 45)).contains("45 secondes pour accepter");
        assertThat(body("ja-JP", 45)).contains("残り45秒");
    }

    @Test
    void selectedWorkerGetsPlaceTimeAndItems() {
        Map<String, Object> args = new HashMap<>();
        args.put("title", "Auxiliar de bodega");
        args.put("address", "Calle 26");
        args.put("when", "jueves 8:00");
        args.put("until", "jueves 16:00");
        args.put("items", "Botas, Guantes");
        String body = templates.render("vacancy.SELECTED.worker", Locale.forLanguageTag("es-CO"), args)
                .orElseThrow().body();
        assertThat(body).contains("Calle 26").contains("jueves 8:00").contains("Debe llevar: Botas, Guantes.")
                .contains("¿Podrá asistir?");
        args.put("items", "none");
        assertThat(templates.render("vacancy.SELECTED.worker", Locale.forLanguageTag("es-CO"), args)
                .orElseThrow().body()).contains("No es necesario llevar implementos.");
        assertThat(templates.exists("vacancy.CONFIRMED.employer")).isTrue();
        assertThat(templates.exists("vacancy.CONFIRMED.worker")).isFalse();
    }

    @Test
    void unknownLanguageFallsBackToEnglish() {
        assertThat(body("de-DE", 2)).startsWith("New job: Bodega");
    }

    @Test
    void audienceWithoutTemplateIsNotNotified() {
        assertThat(templates.exists("status.ASSIGNED.client")).isTrue();
        assertThat(templates.exists("status.EXPIRED.worker")).isFalse();
        assertThat(templates.render("status.EXPIRED.worker", Locale.forLanguageTag("es-CO"), Map.of())).isEmpty();
    }

    @Test
    void timeIsWrittenInTheRecipientZone() {
        Instant at = Instant.parse("2026-12-01T13:00:00Z");
        String bogota = templates.when(at, Locale.forLanguageTag("es-CO"), ZoneId.of("America/Bogota"));
        String tokyo = templates.when(at, Locale.forLanguageTag("ja-JP"), ZoneId.of("Asia/Tokyo"));
        assertThat(bogota).contains("8:00").contains("dic");
        assertThat(tokyo).contains("22:00").contains("12月1日");
    }

    private String body(String tag, long seconds) {
        return templates.render("offer.worker", Locale.forLanguageTag(tag), offer(seconds)).orElseThrow().body();
    }
}
