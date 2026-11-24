package jornal.common.observability;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Id de correlación de punta a punta: lo pone el gateway ({@code X-Correlation-Id}), cada proceso lo guarda en
 * el MDC ({@code cid}, aparece en cada línea de log) y viaja en cada mensaje como {@code correlationid}.
 */
public final class Correlation {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "cid";

    private Correlation() {
    }

    /** El id de la petición o el mensaje en curso, o {@code null} si no hay. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    /** El de la petición en curso si existe; si no, {@code fallback}. */
    public static String currentOr(String fallback) {
        String cid = current();
        return cid == null || cid.isBlank() ? fallback : cid;
    }

    /** Pone el id en el MDC mientras dure el bloque: {@code try (var c = Correlation.open(cid)) {...}}. */
    public static MDC.MDCCloseable open(String cid) {
        return MDC.putCloseable(MDC_KEY, cid == null || cid.isBlank() ? UUID.randomUUID().toString() : sanitize(cid));
    }

    /** Evita inyección en los logs: solo letras, números, guiones y hasta 64 caracteres. */
    public static String sanitize(String cid) {
        String clean = cid.replaceAll("[^A-Za-z0-9._-]", "");
        return clean.isEmpty() ? UUID.randomUUID().toString() : clean.substring(0, Math.min(64, clean.length()));
    }
}
