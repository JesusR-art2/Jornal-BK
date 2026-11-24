package jornal.payments;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PaymentRepository {

    private static final RowMapper<Payment> MAPPER = (rs, i) -> new Payment(
            rs.getObject("id", UUID.class), rs.getString("region"), rs.getObject("request_id", UUID.class),
            rs.getInt("attempt"), rs.getObject("client_id", UUID.class), rs.getObject("worker_id", UUID.class),
            rs.getString("method"), rs.getString("flow"), rs.getString("currency"), rs.getLong("amount_minor"),
            rs.getString("status"), rs.getString("provider_ref"), rs.getString("voucher_code"),
            instant(rs.getTimestamp("voucher_expires_at")), rs.getString("failure_reason"),
            rs.getString("request_data"), instant(rs.getTimestamp("created_at")),
            instant(rs.getTimestamp("updated_at")));

    private final JdbcTemplate jdbc;

    public PaymentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserta el intento. Devuelve {@code false} si el pedido ya tenía ese intento (no se cobra dos veces). */
    public boolean insertIfAbsent(Payment p) {
        return jdbc.update("""
                INSERT INTO payments (id, region, request_id, attempt, client_id, worker_id, method, flow, currency,
                                      amount_minor, status, request_data, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                ON CONFLICT (region, request_id, attempt) DO NOTHING
                """, p.id(), p.region(), p.requestId(), p.attempt(), p.clientId(), p.workerId(), p.method(),
                p.flow(), p.currency(), p.amountMinor(), p.status(), p.requestData()) == 1;
    }

    /** Cambio de estado condicionado al estado esperado (evita carreras entre el listener y las tareas). */
    public boolean transition(String region, UUID id, String expected, String next, String providerRef,
                              String failureReason) {
        return jdbc.update("""
                UPDATE payments SET status = ?, provider_ref = COALESCE(?, provider_ref),
                       failure_reason = COALESCE(?, failure_reason), updated_at = now()
                 WHERE region = ? AND id = ? AND status = ?
                """, next, providerRef, failureReason, region, id, expected) == 1;
    }

    public void setVoucher(String region, UUID id, String code, Instant expiresAt) {
        jdbc.update("UPDATE payments SET voucher_code = ?, voucher_expires_at = ? WHERE region = ? AND id = ?",
                code, Timestamp.from(expiresAt), region, id);
    }

    public void setWorkerAndData(String region, UUID id, UUID workerId, String requestData) {
        jdbc.update("UPDATE payments SET worker_id = ?, request_data = ? WHERE region = ? AND id = ?",
                workerId, requestData, region, id);
    }

    public Optional<Payment> find(String region, UUID requestId, int attempt) {
        return jdbc.query("SELECT * FROM payments WHERE region = ? AND request_id = ? AND attempt = ?",
                MAPPER, region, requestId, attempt).stream().findFirst();
    }

    public Optional<Payment> findById(UUID id) {
        return jdbc.query("SELECT * FROM payments WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public List<Payment> findByRequest(String region, UUID requestId) {
        return jdbc.query("SELECT * FROM payments WHERE region = ? AND request_id = ? ORDER BY attempt",
                MAPPER, region, requestId);
    }

    public List<Payment> findByRequestId(UUID requestId) {
        return jdbc.query("SELECT * FROM payments WHERE request_id = ? ORDER BY attempt", MAPPER, requestId);
    }

    public List<Payment> pendingVouchersCreatedBefore(Instant before) {
        return jdbc.query("SELECT * FROM payments WHERE status = 'PENDING_VOUCHER' AND created_at < ? LIMIT 100",
                MAPPER, Timestamp.from(before));
    }

    public List<Payment> expiredVouchers(Instant now) {
        return jdbc.query("SELECT * FROM payments WHERE status = 'PENDING_VOUCHER' AND voucher_expires_at < ? LIMIT 100",
                MAPPER, Timestamp.from(now));
    }

    // ------------------------------------------------------------------ libro de movimientos

    public void ledger(Payment p, String kind, long amountMinor) {
        jdbc.update("""
                INSERT INTO ledger_entries (id, region, request_id, payment_id, kind, amount_minor, currency, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, now())
                """, UUID.randomUUID(), p.region(), p.requestId(), p.id(), kind, amountMinor, p.currency());
    }

    public List<Map<String, Object>> ledgerOf(UUID requestId) {
        return jdbc.queryForList("""
                SELECT kind, amount_minor, currency, created_at FROM ledger_entries
                 WHERE request_id = ? ORDER BY created_at, kind
                """, requestId);
    }

    /** Totales por región, moneda y tipo de movimiento (base del reporte de ingresos). */
    public List<Map<String, Object>> summary() {
        return jdbc.queryForList("""
                SELECT region, currency, kind, sum(amount_minor) AS amount_minor, count(*) AS entries
                  FROM ledger_entries GROUP BY region, currency, kind ORDER BY region, kind
                """);
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
