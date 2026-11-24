package jornal.dispatch;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** Acceso a datos de asignación (JDBC). */
@Repository
public class DispatchRepository {

    public record WorkerRow(String region, UUID workerId, String name, boolean eligible, boolean available,
                            Set<UUID> categoryIds, int radiusKm, Double baseLatitude, Double baseLongitude,
                            double ratingAvg, int ratingCount, long version) {
    }

    public record Assignment(UUID requestId, String region, UUID clientId, UUID categoryId, String categoryName,
                             String addressText, double latitude, double longitude, Instant scheduledAt,
                             String status, UUID workerId, int offersMade, Instant createdAt) {
    }

    public record Offer(UUID id, String region, UUID requestId, UUID workerId, String status, double distanceKm,
                        Instant expiresAt, Instant createdAt) {
    }

    private static final RowMapper<WorkerRow> WORKER = (rs, i) -> new WorkerRow(
            rs.getString("region"), rs.getObject("worker_id", UUID.class), rs.getString("name"),
            rs.getBoolean("eligible"), rs.getBoolean("available"), uuids(rs.getString("category_ids")),
            rs.getInt("radius_km"), (Double) rs.getObject("base_latitude"), (Double) rs.getObject("base_longitude"),
            rs.getDouble("rating_avg"), rs.getInt("rating_count"), rs.getLong("version"));

    private static final RowMapper<Assignment> ASSIGNMENT = (rs, i) -> new Assignment(
            rs.getObject("request_id", UUID.class), rs.getString("region"), rs.getObject("client_id", UUID.class),
            rs.getObject("category_id", UUID.class), rs.getString("category_name"), rs.getString("address_text"),
            rs.getDouble("latitude"), rs.getDouble("longitude"), rs.getTimestamp("scheduled_at").toInstant(),
            rs.getString("status"), rs.getObject("worker_id", UUID.class), rs.getInt("offers_made"),
            rs.getTimestamp("created_at").toInstant());

    private static final RowMapper<Offer> OFFER = (rs, i) -> new Offer(
            rs.getObject("id", UUID.class), rs.getString("region"), rs.getObject("request_id", UUID.class),
            rs.getObject("worker_id", UUID.class), rs.getString("status"), rs.getDouble("distance_km"),
            rs.getTimestamp("expires_at").toInstant(), rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbc;

    public DispatchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ proyección de trabajadores

    /** Guarda la foto del trabajador solo si es más nueva que la que había. */
    public boolean upsertWorker(WorkerRow w) {
        return jdbc.update("""
                INSERT INTO worker_projection (region, worker_id, name, eligible, available, category_ids, radius_km,
                       base_latitude, base_longitude, rating_avg, rating_count, version, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (region, worker_id) DO UPDATE SET
                       name = EXCLUDED.name, eligible = EXCLUDED.eligible, available = EXCLUDED.available,
                       category_ids = EXCLUDED.category_ids, radius_km = EXCLUDED.radius_km,
                       base_latitude = EXCLUDED.base_latitude, base_longitude = EXCLUDED.base_longitude,
                       rating_avg = EXCLUDED.rating_avg, rating_count = EXCLUDED.rating_count,
                       version = EXCLUDED.version, updated_at = now()
                 WHERE worker_projection.version < EXCLUDED.version
                """, w.region(), w.workerId(), w.name(), w.eligible(), w.available(), csv(w.categoryIds()),
                w.radiusKm(), w.baseLatitude(), w.baseLongitude(), w.ratingAvg(), w.ratingCount(), w.version()) == 1;
    }

    public List<WorkerRow> availableWorkers(String region) {
        return jdbc.query("SELECT * FROM worker_projection WHERE region = ? AND eligible AND available", WORKER, region);
    }

    public Optional<WorkerRow> worker(String region, UUID workerId) {
        return jdbc.query("SELECT * FROM worker_projection WHERE region = ? AND worker_id = ?", WORKER, region, workerId)
                .stream().findFirst();
    }

    // ------------------------------------------------------------------ búsquedas

    public boolean insertAssignment(Assignment a) {
        return jdbc.update("""
                INSERT INTO assignments (request_id, region, client_id, category_id, category_name, address_text,
                       latitude, longitude, scheduled_at, status, offers_made, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'SEARCHING', 0, now(), now())
                ON CONFLICT (request_id) DO NOTHING
                """, a.requestId(), a.region(), a.clientId(), a.categoryId(), a.categoryName(), a.addressText(),
                a.latitude(), a.longitude(), Timestamp.from(a.scheduledAt())) == 1;
    }

    /** Bloquea la fila: una sola transacción a la vez decide la siguiente oferta del pedido. */
    public Optional<Assignment> lockAssignment(UUID requestId) {
        return jdbc.query("SELECT * FROM assignments WHERE request_id = ? FOR UPDATE", ASSIGNMENT, requestId)
                .stream().findFirst();
    }

    public Optional<Assignment> assignment(UUID requestId) {
        return jdbc.query("SELECT * FROM assignments WHERE request_id = ?", ASSIGNMENT, requestId).stream().findFirst();
    }

    public void updateAssignment(UUID requestId, String status, UUID workerId, int offersMade) {
        jdbc.update("""
                UPDATE assignments SET status = ?, worker_id = COALESCE(?, worker_id), offers_made = ?, updated_at = now()
                 WHERE request_id = ?
                """, status, workerId, offersMade, requestId);
    }

    public List<UUID> searchingBefore(Instant before) {
        return jdbc.queryForList("""
                SELECT request_id FROM assignments WHERE status = 'SEARCHING' AND updated_at < ? LIMIT 100
                """, UUID.class, Timestamp.from(before));
    }

    /** Pedidos asignados hoy por trabajador (balanceo de carga). */
    public Map<UUID, Integer> assignedSince(String region, Instant since) {
        Map<UUID, Integer> counts = new HashMap<>();
        jdbc.query("""
                SELECT worker_id, count(*) AS n FROM assignments
                 WHERE region = ? AND worker_id IS NOT NULL AND status IN ('ASSIGNED', 'DONE') AND updated_at >= ?
                 GROUP BY worker_id
                """, rs -> {
            counts.put(rs.getObject("worker_id", UUID.class), rs.getInt("n"));
        }, region, Timestamp.from(since));
        return counts;
    }

    // ------------------------------------------------------------------ ofertas

    /** Devuelve {@code false} si el trabajador o el pedido ya tienen una oferta pendiente. */
    public boolean insertOffer(Offer o) {
        return jdbc.update("""
                INSERT INTO offers (id, region, request_id, worker_id, status, distance_km, expires_at, created_at)
                VALUES (?, ?, ?, ?, 'PENDING', ?, ?, now())
                ON CONFLICT DO NOTHING
                """, o.id(), o.region(), o.requestId(), o.workerId(), o.distanceKm(), Timestamp.from(o.expiresAt())) == 1;
    }

    public Optional<Offer> lockOffer(UUID offerId) {
        return jdbc.query("SELECT * FROM offers WHERE id = ? FOR UPDATE", OFFER, offerId).stream().findFirst();
    }

    public boolean closeOffer(UUID offerId, String from, String to) {
        return jdbc.update("UPDATE offers SET status = ?, responded_at = now() WHERE id = ? AND status = ?",
                to, offerId, from) == 1;
    }

    public void cancelPendingOffers(UUID requestId) {
        jdbc.update("UPDATE offers SET status = 'CANCELLED', responded_at = now() WHERE request_id = ? AND status = 'PENDING'",
                requestId);
    }

    public List<Offer> pendingOffersOf(UUID workerId) {
        return jdbc.query("SELECT * FROM offers WHERE worker_id = ? AND status = 'PENDING' ORDER BY created_at",
                OFFER, workerId);
    }

    public List<Offer> expiredOffers(Instant now) {
        return jdbc.query("SELECT * FROM offers WHERE status = 'PENDING' AND expires_at < ? LIMIT 100",
                OFFER, Timestamp.from(now));
    }

    public Set<UUID> workersOfferedFor(UUID requestId) {
        return new HashSet<>(jdbc.queryForList("SELECT worker_id FROM offers WHERE request_id = ?", UUID.class, requestId));
    }

    public Set<UUID> workersWithPendingOffer() {
        return new HashSet<>(jdbc.queryForList("SELECT worker_id FROM offers WHERE status = 'PENDING'", UUID.class));
    }

    // ------------------------------------------------------------------ reservas

    /** Reserva atómica: devuelve {@code false} si el trabajador ya tiene otro pedido o el pedido otro trabajador. */
    public boolean reserve(UUID workerId, String region, UUID requestId) {
        return jdbc.update("""
                INSERT INTO reservations (worker_id, region, request_id, reserved_at) VALUES (?, ?, ?, now())
                ON CONFLICT DO NOTHING
                """, workerId, region, requestId) == 1;
    }

    public int releaseRequest(UUID requestId) {
        return jdbc.update("DELETE FROM reservations WHERE request_id = ?", requestId);
    }

    public int release(UUID workerId, UUID requestId) {
        return jdbc.update("DELETE FROM reservations WHERE worker_id = ? AND request_id = ?", workerId, requestId);
    }

    public Set<UUID> reservedWorkers() {
        return new HashSet<>(jdbc.queryForList("SELECT worker_id FROM reservations", UUID.class));
    }

    public Optional<UUID> reservedRequestOf(UUID workerId) {
        return jdbc.queryForList("SELECT request_id FROM reservations WHERE worker_id = ?", UUID.class, workerId)
                .stream().findFirst();
    }

    public long countReservations(UUID workerId) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM reservations WHERE worker_id = ?", Long.class, workerId);
        return n == null ? 0 : n;
    }

    // ------------------------------------------------------------------ utilidades

    static String csv(Collection<UUID> ids) {
        return String.join(",", ids.stream().map(UUID::toString).toList());
    }

    static Set<UUID> uuids(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        Set<UUID> set = new HashSet<>();
        for (String s : csv.split(",")) set.add(UUID.fromString(s.trim()));
        return set;
    }
}
