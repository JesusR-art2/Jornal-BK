package jornal.notifications;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class NotificationRepository {

    private final JdbcTemplate jdbc;

    public NotificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Delivery(UUID id, String region, String eventId, UUID requestId, UUID recipientId,
                           String channel, String template, String locale, String timezone, String destination,
                           String subject, String body, String status, String error, Instant createdAt) {
    }

    /** Participantes del pedido; el trabajador no se borra si un evento llega sin él. */
    public void upsertParticipants(UUID requestId, String region, UUID clientId, UUID workerId, String status,
                                   Instant at) {
        jdbc.update("""
                INSERT INTO request_participants (request_id, region, client_id, worker_id, status, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (request_id) DO UPDATE
                   SET worker_id = COALESCE(EXCLUDED.worker_id, request_participants.worker_id),
                       status = EXCLUDED.status, updated_at = EXCLUDED.updated_at
                """, requestId, region, clientId, workerId, status, Timestamp.from(at));
    }

    public boolean isParticipant(UUID requestId, UUID userId) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM request_participants
                 WHERE request_id = ? AND (client_id = ? OR worker_id = ?)
                """, Integer.class, requestId, userId, userId);
        return n != null && n > 0;
    }

    public boolean exists(String eventId, UUID recipientId, String channel) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE event_id = ? AND recipient_id = ? AND channel = ?",
                Integer.class, eventId, recipientId, channel);
        return n != null && n > 0;
    }

    public boolean insert(Delivery d) {
        return jdbc.update("""
                INSERT INTO notifications (id, region, event_id, request_id, recipient_id, channel, template, locale,
                                           timezone, destination, subject, body, status, error, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id, recipient_id, channel) DO NOTHING
                """, d.id(), d.region(), d.eventId(), d.requestId(), d.recipientId(), d.channel(), d.template(),
                d.locale(), d.timezone(), d.destination(), d.subject(), d.body(), d.status(), d.error(),
                Timestamp.from(d.createdAt())) == 1;
    }

    /** Bandeja de la app: avisos IN_APP del usuario, el más reciente primero. */
    public List<Map<String, Object>> inbox(UUID recipientId, int limit) {
        return jdbc.queryForList("""
                SELECT id, request_id AS "requestId", template, subject AS title, body, locale,
                       created_at AS "createdAt", read_at AS "readAt"
                  FROM notifications
                 WHERE recipient_id = ? AND channel = 'IN_APP'
                 ORDER BY created_at DESC
                 LIMIT ?
                """, recipientId, limit);
    }

    public boolean markRead(UUID recipientId, UUID id, Instant at) {
        return jdbc.update("""
                UPDATE notifications SET read_at = ?
                 WHERE id = ? AND recipient_id = ? AND channel = 'IN_APP' AND read_at IS NULL
                """, Timestamp.from(at), id, recipientId) == 1;
    }

    /** Entregas de un pedido en todos los canales, sin el destino (no expone correo ni teléfono). */
    public List<Map<String, Object>> byRequest(UUID requestId) {
        return jdbc.queryForList("""
                SELECT recipient_id AS "recipientId", channel, template, locale, timezone, subject, body, status,
                       created_at AS "createdAt"
                  FROM notifications
                 WHERE request_id = ?
                 ORDER BY created_at, template, channel
                """, requestId);
    }
}
