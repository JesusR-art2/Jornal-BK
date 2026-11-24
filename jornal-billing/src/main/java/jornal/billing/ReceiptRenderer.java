package jornal.billing;

import com.ibm.icu.text.DateFormat;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.util.Currency;
import com.ibm.icu.util.TimeZone;
import com.ibm.icu.util.ULocale;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.util.*;

/**
 * Presenta el comprobante en el idioma del país (o el pedido con {@code lang}): montos con CLDR y la escala de
 * la moneda del perfil, fecha y hora en la zona del lugar del trabajo, impuestos con su tasa. Salida JSON
 * (con los valores ya formateados) y HTML imprimible.
 */
@Component
public class ReceiptRenderer {

    private final MessageSource labels;

    public ReceiptRenderer(MessageSource labels) {
        this.labels = labels;
    }

    public record Line(String kind, String description, long quantity, String unitPrice, String amount,
                       boolean includedInTotal) {
    }

    public record View(Receipt receipt, String language, String title, String issuedAtText, List<Line> lines,
                       Map<String, String> totals) {
    }

    public View view(Receipt r, String lang) {
        Locale locale = Locale.forLanguageTag(lang == null || lang.isBlank() ? r.locale() : lang);
        List<Line> lines = new ArrayList<>();
        for (Map<String, Object> l : r.lines()) {
            String kind = String.valueOf(l.get("kind"));
            lines.add(new Line(kind, description(l, kind, locale), number(l.get("quantity")),
                    money(number(l.get("unitAmountMinor")), r, locale), money(number(l.get("amountMinor")), r, locale),
                    Boolean.TRUE.equals(l.get("includedInTotal"))));
        }
        Map<String, String> totals = new LinkedHashMap<>();
        totals.put(label("label.subtotal", locale), money(r.subtotalMinor(), r, locale));
        if (r.travelFeeMinor() > 0) totals.put(label("label.travelFee", locale), money(r.travelFeeMinor(), r, locale));
        for (Line line : lines) {
            if ("TAX".equals(line.kind())) totals.put(line.description(), line.amount());
        }
        if (r.tipMinor() > 0) totals.put(label("label.tip", locale), money(r.tipMinor(), r, locale));
        totals.put(label("label.total", locale), money(r.totalMinor(), r, locale));
        return new View(r, locale.toLanguageTag(), label("type." + r.receiptType(), locale),
                date(r, locale), lines, totals);
    }

    public String html(Receipt r, String lang) {
        View v = view(r, lang);
        Locale locale = Locale.forLanguageTag(v.language());
        StringBuilder rows = new StringBuilder();
        for (Line l : v.lines()) {
            if ("TAX".equals(l.kind())) continue;   // los impuestos van en los totales con su tasa
            rows.append("<tr><td>").append(esc(l.description())).append("</td><td class=n>").append(l.quantity())
                    .append("</td><td class=n>").append(esc(l.unitPrice())).append("</td><td class=n>")
                    .append(esc(l.amount())).append("</td></tr>\n");
        }
        StringBuilder totals = new StringBuilder();
        v.totals().forEach((k, val) -> totals.append("<tr><th>").append(esc(k)).append("</th><td class=n>")
                .append(esc(val)).append("</td></tr>\n"));
        String customer = Optional.ofNullable(r.customerLegalName()).orElse(Optional.ofNullable(r.customerName()).orElse(""));
        String customerTax = r.customerTaxIdNumber() == null ? ""
                : "<br>" + esc(label("label.taxId", locale)) + ": " + esc(r.customerTaxIdType()) + " " + esc(r.customerTaxIdNumber());
        return """
                <!doctype html>
                <html lang="%s"><head><meta charset="utf-8"><title>%s %s</title>
                <style>
                body{font-family:"Noto Sans","Noto Sans JP",system-ui,sans-serif;max-width:760px;margin:24px auto;padding:0 16px;color:#111}
                h1{font-size:1.4rem;margin:0}.muted{color:#555}table{width:100%%;border-collapse:collapse;margin-top:16px}
                th,td{padding:6px 4px;border-bottom:1px solid #ddd;text-align:left}.n{text-align:right;white-space:nowrap}
                .totals{width:60%%;margin-left:auto}.grid{display:flex;gap:24px;margin-top:16px}.grid div{flex:1}
                @media print{body{margin:0}}
                </style></head><body>
                <h1>%s</h1>
                <p class="muted">%s: <strong>%s</strong> · %s: %s (%s)</p>
                <div class="grid"><div><strong>%s</strong><br>%s<br>%s</div>
                <div><strong>%s</strong><br>%s%s</div></div>
                <p>%s: %s</p>
                <table><thead><tr><th>%s</th><th class=n>%s</th><th class=n>%s</th><th class=n>%s</th></tr></thead>
                <tbody>
                %s</tbody></table>
                <table class="totals"><tbody>
                %s</tbody></table>
                <p class="muted">%s</p>
                </body></html>
                """.formatted(esc(v.language()), esc(v.title()), esc(r.number()), esc(v.title()),
                esc(label("label.number", locale)), esc(r.number()), esc(label("label.date", locale)),
                esc(v.issuedAtText()), esc(r.timezone()),
                esc(label("label.issuer", locale)), esc(r.issuerName()), esc(r.issuerTaxId()),
                esc(label("label.customer", locale)), esc(customer), customerTax,
                esc(label("label.address", locale)), esc(Objects.toString(r.addressText(), "")),
                esc(label("label.description", locale)), esc(label("label.quantity", locale)),
                esc(label("label.unitPrice", locale)), esc(label("label.amount", locale)),
                rows, totals, esc(label("note.demo", locale)));
    }

    /** Monto en unidades menores con el formato CLDR del idioma y la escala de la moneda del perfil. */
    public static String money(long minor, String currency, int scale, Locale locale) {
        NumberFormat format = NumberFormat.getCurrencyInstance(ULocale.forLocale(locale));
        format.setCurrency(Currency.getInstance(currency));
        format.setMinimumFractionDigits(scale);
        format.setMaximumFractionDigits(scale);
        return format.format(BigDecimal.valueOf(minor, scale));
    }

    private static String money(long minor, Receipt r, Locale locale) {
        return money(minor, r.currency(), r.moneyScale(), locale);
    }

    private String description(Map<String, Object> line, String kind, Locale locale) {
        String text = String.valueOf(line.get("description"));
        return switch (kind) {
            case "TRAVEL_FEE" -> label("label.travel_fee", locale);
            case "TIP" -> label("label.tipLine", locale);
            case "TAX" -> {
                String mode = label(Boolean.TRUE.equals(line.get("includedInTotal")) ? "label.added" : "label.included",
                        locale);
                if (line.get("rateBps") instanceof Number bps) {
                    NumberFormat percent = NumberFormat.getPercentInstance(ULocale.forLocale(locale));
                    percent.setMaximumFractionDigits(2);
                    yield text + " (" + percent.format(bps.doubleValue() / 10_000) + ", " + mode + ")";
                }
                yield text + " (" + mode + ")";
            }
            default -> text;
        };
    }

    private static String date(Receipt r, Locale locale) {
        DateFormat format = DateFormat.getInstanceForSkeleton("yMMMdjmm", ULocale.forLocale(locale));
        format.setTimeZone(TimeZone.getTimeZone(r.timezone()));
        return format.format(Date.from(r.issuedAt()));
    }

    private String label(String code, Locale locale) {
        return labels.getMessage(code, null, code, locale);
    }

    private static long number(Object value) {
        return value instanceof Number n ? n.longValue() : value == null ? 0 : Long.parseLong(value.toString());
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s, "UTF-8");
    }
}
