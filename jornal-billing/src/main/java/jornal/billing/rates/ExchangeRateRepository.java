package jornal.billing.rates;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class ExchangeRateRepository {

    private final JdbcTemplate jdbc;

    public ExchangeRateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 1 {@code base} = {@code rate} {@code quote}, según {@code source} el día {@code asOf}. */
    public record Rate(String base, String quote, BigDecimal rate, String source, LocalDate asOf) {
    }

    /** Guarda la tasa; si ya existe el mismo par, día y fuente, la reemplaza (carga manual corregida). */
    public void save(Rate r, Instant at) {
        jdbc.update("""
                INSERT INTO exchange_rates (base, quote, rate, source, as_of, created_at) VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (base, quote, as_of, source) DO UPDATE SET rate = EXCLUDED.rate, created_at = EXCLUDED.created_at
                """, r.base(), r.quote(), r.rate(), r.source(), Date.valueOf(r.asOf()), Timestamp.from(at));
    }

    /** La más reciente del par con fecha menor o igual a {@code onOrBefore}. */
    public Optional<Rate> latest(String base, String quote, LocalDate onOrBefore) {
        return jdbc.query("""
                SELECT base, quote, rate, source, as_of FROM exchange_rates
                 WHERE base = ? AND quote = ? AND as_of <= ?
                 ORDER BY as_of DESC, created_at DESC LIMIT 1
                """, (rs, i) -> new Rate(rs.getString(1), rs.getString(2), rs.getBigDecimal(3), rs.getString(4),
                rs.getDate(5).toLocalDate()), base, quote, Date.valueOf(onOrBefore)).stream().findFirst();
    }

    public List<Rate> recent(int limit) {
        return jdbc.query("""
                SELECT base, quote, rate, source, as_of FROM exchange_rates ORDER BY as_of DESC, base, quote LIMIT ?
                """, (rs, i) -> new Rate(rs.getString(1), rs.getString(2), rs.getBigDecimal(3), rs.getString(4),
                rs.getDate(5).toLocalDate()), limit);
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM exchange_rates", Long.class);
        return n == null ? 0 : n;
    }
}
