package jornal.common.events;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Sobre CloudEvents 1.0 en modo estructurado (JSON). Lo usan todos los mensajes entre procesos.
 *
 * @param region           código de la región en minúsculas (co, br, us, fr, jp); forma la clave de enrutamiento
 * @param correlationid    une todos los mensajes de un mismo flujo
 * @param aggregateversion crece por agregado; el consumidor descarta versiones viejas
 */
public record CloudEvent(
        String specversion,
        String id,
        String source,
        String type,
        Instant time,
        String subject,
        String datacontenttype,
        String region,
        String correlationid,
        long aggregateversion,
        Map<String, Object> data) {

    public static final String SPEC_VERSION = "1.0";

    public static CloudEvent of(String source, String type, String region, String subject,
                                long aggregateVersion, Map<String, Object> data, Instant time,
                                String correlationId) {
        String id = UUID.randomUUID().toString();
        return new CloudEvent(SPEC_VERSION, id, source, type, time, subject, "application/json",
                region.toLowerCase(Locale.ROOT), correlationId == null ? id : correlationId,
                aggregateVersion, data == null ? Map.of() : data);
    }

    /** Clave de enrutamiento en el exchange topic: {@code <región>.<type>}. */
    public String routingKey() {
        return region + "." + type;
    }
}
