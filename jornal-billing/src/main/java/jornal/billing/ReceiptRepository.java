package jornal.billing;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ReceiptRepository {

    private static final TypeReference<List<Map<String, Object>>> LINES = new TypeReference<>() { };

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public ReceiptRepository(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Siguiente número consecutivo de la región y el tipo (atómico; se revierte con la transacción). */
    public long nextNumber(String region, String receiptType) {
        Long next = jdbc.queryForObject("""
                INSERT INTO receipt_sequences (region, receipt_type, last_number) VALUES (?, ?, 1)
                ON CONFLICT (region, receipt_type) DO UPDATE SET last_number = receipt_sequences.last_number + 1
                RETURNING last_number
                """, Long.class, region, receiptType);
        return next == null ? 1 : next;
    }

    public boolean existsForRequest(String region, UUID requestId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM receipts WHERE region = ? AND request_id = ?",
                Integer.class, region, requestId);
        return n != null && n > 0;
    }

    public void insert(Receipt r) {
        jdbc.update("""
                INSERT INTO receipts (id, region, request_id, receipt_type, sequence, number, client_id, worker_id,
                    issuer_name, issuer_tax_id, customer_name, customer_legal_name, customer_tax_id_type,
                    customer_tax_id_number, locale, timezone, issued_at, issued_local, currency, money_scale,
                    subtotal_minor, travel_fee_minor, tax_minor, tax_included, tip_minor, total_minor,
                    platform_revenue_minor, category_name, address_text, lines)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, r.id(), r.region(), r.requestId(), r.receiptType(), r.sequence(), r.number(), r.clientId(),
                r.workerId(), r.issuerName(), r.issuerTaxId(), r.customerName(), r.customerLegalName(),
                r.customerTaxIdType(), r.customerTaxIdNumber(), r.locale(), r.timezone(), Timestamp.from(r.issuedAt()),
                r.issuedLocal(), r.currency(), r.moneyScale(), r.subtotalMinor(), r.travelFeeMinor(), r.taxMinor(),
                r.taxIncluded(), r.tipMinor(), r.totalMinor(), r.platformRevenueMinor(), r.categoryName(),
                r.addressText(), json.writeValueAsString(r.lines()));
    }

    public Optional<Receipt> findByRequest(UUID requestId) {
        return jdbc.query("SELECT * FROM receipts WHERE request_id = ?", mapper(), requestId).stream().findFirst();
    }

    public List<Receipt> list(String region, int limit) {
        if (region == null) {
            return jdbc.query("SELECT * FROM receipts ORDER BY issued_at DESC LIMIT ?", mapper(), limit);
        }
        return jdbc.query("SELECT * FROM receipts WHERE region = ? ORDER BY issued_at DESC LIMIT ?", mapper(),
                region, limit);
    }

    /** Totales por región y moneda en el rango [from, to) de emisión. */
    public record Totals(String region, String currency, int moneyScale, long receipts, long totalMinor,
                         long platformRevenueMinor, long taxMinor) {
    }

    public List<Totals> totals(Instant from, Instant to) {
        return jdbc.query("""
                SELECT region, currency, max(money_scale) AS scale, count(*) AS n, sum(total_minor) AS total,
                       sum(platform_revenue_minor) AS revenue, sum(tax_minor) AS tax
                  FROM receipts
                 WHERE issued_at >= ? AND issued_at < ?
                 GROUP BY region, currency
                 ORDER BY region
                """, (rs, i) -> new Totals(rs.getString("region"), rs.getString("currency"), rs.getInt("scale"),
                rs.getLong("n"), rs.getLong("total"), rs.getLong("revenue"), rs.getLong("tax")),
                Timestamp.from(from), Timestamp.from(to));
    }

    private RowMapper<Receipt> mapper() {
        return (rs, i) -> new Receipt(
                rs.getObject("id", UUID.class), rs.getString("region"), rs.getObject("request_id", UUID.class),
                rs.getString("receipt_type"), rs.getLong("sequence"), rs.getString("number"),
                rs.getObject("client_id", UUID.class), rs.getObject("worker_id", UUID.class),
                rs.getString("issuer_name"), rs.getString("issuer_tax_id"), rs.getString("customer_name"),
                rs.getString("customer_legal_name"), rs.getString("customer_tax_id_type"),
                rs.getString("customer_tax_id_number"), rs.getString("locale"), rs.getString("timezone"),
                rs.getTimestamp("issued_at").toInstant(), rs.getString("issued_local"), rs.getString("currency"),
                rs.getInt("money_scale"), rs.getLong("subtotal_minor"), rs.getLong("travel_fee_minor"),
                rs.getLong("tax_minor"), rs.getBoolean("tax_included"), rs.getLong("tip_minor"),
                rs.getLong("total_minor"), rs.getLong("platform_revenue_minor"), rs.getString("category_name"),
                rs.getString("address_text"), json.readValue(rs.getString("lines"), LINES));
    }
}
