package jornal.common.subsystem;

import jornal.common.events.CloudEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Outbox de un subsistema. {@link #enqueue} debe llamarse dentro de la transacción del cambio:
 * si la transacción se revierte, el mensaje tampoco sale.
 */
@Component
public class SubsystemOutbox {

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final SubsystemProperties properties;

    public SubsystemOutbox(JdbcTemplate jdbc, JsonMapper json, SubsystemProperties properties) {
        this.jdbc = jdbc;
        this.json = json;
        this.properties = properties;
    }

    /** Arma el CloudEvent con {@code source = jornal-<subsistema>} y lo deja pendiente de envío. */
    public CloudEvent publish(String type, CloudEvent cause, String subject, Map<String, Object> data) {
        CloudEvent event = CloudEvent.of("jornal-" + properties.name(), type, cause.region(), subject,
                System.currentTimeMillis(), data, Instant.now(), cause.correlationid());
        enqueue(event);
        return event;
    }

    public CloudEvent publish(String type, String region, String subject, String correlationId,
                              Map<String, Object> data) {
        CloudEvent event = CloudEvent.of("jornal-" + properties.name(), type, region, subject,
                System.currentTimeMillis(), data, Instant.now(), jornal.common.observability.Correlation.currentOr(correlationId));
        enqueue(event);
        return event;
    }

    public void enqueue(CloudEvent event) {
        jdbc.update("INSERT INTO outbox (id, routing_key, payload, created_at) VALUES (?, ?, ?, ?)",
                UUID.fromString(event.id()), event.routingKey(), json.writeValueAsString(event),
                Timestamp.from(Instant.now()));
    }

    public long pending() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM outbox WHERE sent_at IS NULL", Long.class);
        return n == null ? 0 : n;
    }
}
