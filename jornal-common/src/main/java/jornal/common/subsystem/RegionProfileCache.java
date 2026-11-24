package jornal.common.subsystem;

import jornal.common.events.CloudEvent;
import jornal.common.events.EventTypes;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Copia local del perfil de cada región. Solo aplica versiones más nuevas
 * (la versión del perfil ordena los mensajes sin depender del reloj).
 */
@Component
public class RegionProfileCache implements CloudEventHandler {

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public RegionProfileCache(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public record Entry(String region, long version, Instant updatedAt) {
    }

    @Override
    public boolean supports(String type) {
        return EventTypes.REGION_PROFILE_UPDATED.equals(type);
    }

    @Override
    public void handle(CloudEvent event) {
        jdbc.update("""
                INSERT INTO region_profile_cache (region, version, payload, updated_at)
                VALUES (?, ?, ?, now())
                ON CONFLICT (region) DO UPDATE
                   SET version = EXCLUDED.version, payload = EXCLUDED.payload, updated_at = now()
                 WHERE region_profile_cache.version < EXCLUDED.version
                """, event.region(), event.aggregateversion(), json.writeValueAsString(event.data()));
    }

    public List<Entry> entries() {
        return jdbc.query("SELECT region, version, updated_at FROM region_profile_cache ORDER BY region",
                (rs, i) -> new Entry(rs.getString(1), rs.getLong(2), rs.getTimestamp(3).toInstant()));
    }

    public Map<String, Object> payload(String region) {
        List<String> rows = jdbc.queryForList("SELECT payload FROM region_profile_cache WHERE region = ?",
                String.class, region);
        if (rows.isEmpty()) return null;
        @SuppressWarnings("unchecked")
        Map<String, Object> map = json.readValue(rows.getFirst(), Map.class);
        return map;
    }
}
