package jornal.common.subsystem;

import jornal.common.events.CloudEvent;
import jornal.common.messaging.Topology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Envía los mensajes pendientes del {@link SubsystemOutbox} cada segundo. Usa {@code FOR UPDATE SKIP LOCKED}:
 * con varias réplicas del subsistema, cada mensaje lo toma una sola. Si RabbitMQ no responde, la transacción
 * se revierte y se reintenta en la siguiente vuelta; un reenvío lo descarta el inbox del destino.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    private final JsonMapper json;

    public OutboxRelay(JdbcTemplate jdbc, RabbitTemplate rabbit, JsonMapper json) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
        this.json = json;
    }

    @Scheduled(fixedDelayString = "${jornal.outbox.relay-every:1s}")
    @Transactional
    public void relay() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, routing_key, payload FROM outbox
                 WHERE sent_at IS NULL ORDER BY created_at LIMIT 100
                 FOR UPDATE SKIP LOCKED
                """);
        for (Map<String, Object> row : rows) {
            CloudEvent event = json.readValue((String) row.get("payload"), CloudEvent.class);
            try {
                rabbit.convertAndSend(Topology.EXCHANGE, (String) row.get("routing_key"), event);
            } catch (RuntimeException e) {
                log.warn("Outbox relay paused, broker unavailable: {}", e.getMessage());
                return;   // lo enviado hasta aquí se marca al confirmar la transacción
            }
            jdbc.update("UPDATE outbox SET sent_at = now() WHERE id = ?", (UUID) row.get("id"));
        }
    }
}
