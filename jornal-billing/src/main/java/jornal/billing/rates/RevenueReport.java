package jornal.billing.rates;

import jornal.billing.ReceiptRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Reporte consolidado de ingresos de todas las regiones en la moneda que elija el administrador.
 * Cada fila muestra la tasa usada con su par, fuente y fecha. Los montos originales quedan en moneda local.
 */
@Service
public class RevenueReport {

    private final ReceiptRepository receipts;
    private final ExchangeRateService rates;

    public RevenueReport(ReceiptRepository receipts, ExchangeRateService rates) {
        this.receipts = receipts;
        this.rates = rates;
    }

    public Map<String, Object> build(String currency, LocalDate from, LocalDate to) {
        String target = currency.toUpperCase(Locale.ROOT);
        int targetScale = java.util.Currency.getInstance(target).getDefaultFractionDigits();
        Instant start = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal grandTotal = BigDecimal.ZERO;
        BigDecimal grandRevenue = BigDecimal.ZERO;
        List<String> missing = new ArrayList<>();
        for (ReceiptRepository.Totals t : receipts.totals(start, end)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("region", t.region());
            row.put("currency", t.currency());
            row.put("receipts", t.receipts());
            row.put("billedLocal", BigDecimal.valueOf(t.totalMinor(), t.moneyScale()));
            row.put("platformRevenueLocal", BigDecimal.valueOf(t.platformRevenueMinor(), t.moneyScale()));
            row.put("taxLocal", BigDecimal.valueOf(t.taxMinor(), t.moneyScale()));
            Optional<ExchangeRateService.AppliedRate> rate = rates.rate(t.currency(), target, to);
            if (rate.isPresent()) {
                ExchangeRateService.AppliedRate r = rate.get();
                BigDecimal billed = BigDecimal.valueOf(t.totalMinor(), t.moneyScale()).multiply(r.rate())
                        .setScale(targetScale, RoundingMode.HALF_UP);
                BigDecimal revenue = BigDecimal.valueOf(t.platformRevenueMinor(), t.moneyScale()).multiply(r.rate())
                        .setScale(targetScale, RoundingMode.HALF_UP);
                row.put("rate", Map.of("pair", r.pair(), "rate", r.rate().stripTrailingZeros().toPlainString(),
                        "source", r.source(), "asOf", r.asOf().toString(), "method", r.method()));
                row.put("billed", billed);
                row.put("platformRevenue", revenue);
                grandTotal = grandTotal.add(billed);
                grandRevenue = grandRevenue.add(revenue);
            } else {
                row.put("rate", null);
                missing.add(t.currency() + "/" + target);
            }
            rows.add(row);
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("currency", target);
        report.put("from", from.toString());
        report.put("to", to.toString());
        report.put("note", "Cobros en moneda local; la conversión es solo para el reporte");
        report.put("regions", rows);
        report.put("totalBilled", grandTotal);
        report.put("totalPlatformRevenue", grandRevenue);
        report.put("missingRates", missing);
        return report;
    }
}
