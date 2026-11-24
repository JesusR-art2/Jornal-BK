package jornal.notifications;

import com.ibm.icu.text.DateFormat;
import com.ibm.icu.text.MessageFormat;
import com.ibm.icu.util.TimeZone;
import com.ibm.icu.util.ULocale;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Plantillas por idioma ({@code templates/notifications_<idioma>.properties}) con ICU MessageFormat:
 * plurales según CLDR y números con el formato del idioma. Las fechas se escriben en la zona del destinatario.
 * Respaldo: inglés (archivo base).
 */
@Component
public class Templates {

    private final MessageSource messages;

    public Templates(MessageSource messages) {
        this.messages = messages;
    }

    public record Rendered(String title, String body) {
    }

    /** {@code key} es {@code <plantilla>.<audiencia>}; vacío si esa audiencia no tiene plantilla. */
    public Optional<Rendered> render(String key, Locale locale, Map<String, Object> args) {
        String title = raw(key + ".title", locale);
        String body = raw(key + ".body", locale);
        if (title == null || body == null) {
            return Optional.empty();
        }
        return Optional.of(new Rendered(format(title, locale, args), format(body, locale, args)));
    }

    public boolean exists(String key) {
        return raw(key + ".body", Locale.ROOT) != null;
    }

    /** Fecha y hora con el estilo del idioma, en la zona del destinatario (incluye la zona). */
    public String when(Instant at, Locale locale, ZoneId zone) {
        DateFormat format = DateFormat.getInstanceForSkeleton("yMMMdEjmz", ULocale.forLocale(locale));
        format.setTimeZone(TimeZone.getTimeZone(zone.getId()));
        return format.format(Date.from(at));
    }

    private String raw(String code, Locale locale) {
        return messages.getMessage(code, null, null, locale);
    }

    private static String format(String pattern, Locale locale, Map<String, Object> args) {
        return new MessageFormat(pattern, ULocale.forLocale(locale)).format(args);
    }
}
