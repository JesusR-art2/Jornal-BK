package jornal.common.subsystem;

import jornal.common.events.CloudEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Registro de mensajes procesados: un mismo {@code id} solo se procesa una vez (idempotencia). */
@Repository
public class Inbox {

    private final JdbcTemplate jdbc;

    public Inbox(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Devuelve {@code true} si es la primera vez que llega el mensaje. */
    public boolean firstTime(CloudEvent event) {
        return jdbc.update("""
                INSERT INTO inbox (message_id, type, region, source, received_at)
                VALUES (?, ?, ?, ?, now())
                ON CONFLICT (message_id) DO NOTHING
                """, event.id(), event.type(), event.region(), event.source()) == 1;
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM inbox", Long.class);
        return n == null ? 0 : n;
    }
}
