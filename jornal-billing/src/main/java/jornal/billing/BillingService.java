package jornal.billing;

import jornal.common.events.CloudEvent;
import jornal.common.events.EventTypes;
import jornal.common.subsystem.SubsystemOutbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Emite el comprobante cuando pagos captura el cobro ({@code jornal.payment.captured.v1}). El tipo, el prefijo,
 * el idioma y las tasas de impuesto llegan en el evento desde el perfil regional del core; la numeración es
 * consecutiva por región y tipo. Responde {@code jornal.billing.receipt-issued.v1} y el core cierra el pedido.
 */
@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    private final ReceiptRepository receipts;
    private final SubsystemOutbox outbox;
    private final BillingProperties properties;
    private final Clock clock;

    public BillingService(ReceiptRepository receipts, SubsystemOutbox outbox, BillingProperties properties,
                          Clock clock) {
        this.receipts = receipts;
        this.outbox = outbox;
        this.properties = properties;
        this.clock = clock;
    }

    public void onPaymentCaptured(CloudEvent event) {
        Map<String, Object> d = event.data();
        String region = event.region();
        UUID requestId = UUID.fromString(String.valueOf(d.get("requestId")));
        if (receipts.existsForRequest(region, requestId)) {
            log.info("Receipt for request {} already issued", requestId);
            return;
        }
        String type = text(d.get("receiptType"), "SALES_RECEIPT");
        String prefix = text(d.get("receiptPrefix"), "R");
        long sequence = receipts.nextNumber(region, type);
        String number = prefix + "-" + String.format("%08d", sequence);
        String timezone = text(d.get("timezone"), "UTC");
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        String issuedLocal = LocalDateTime.ofInstant(issuedAt, ZoneId.of(timezone)).toString();
        BillingProperties.Issuer issuer = properties.issuerOf(region);

        Receipt receipt = new Receipt(UUID.randomUUID(), region, requestId, type, sequence, number,
                uuid(d.get("clientId")), uuid(d.get("workerId")), issuer.name(), issuer.taxId(),
                (String) d.get("customerName"), (String) d.get("customerLegalName"),
                (String) d.get("customerTaxIdType"), (String) d.get("customerTaxIdNumber"),
                text(d.get("regionLocale"), "en-US"), timezone, issuedAt, issuedLocal,
                text(d.get("currency"), "USD"), (int) number(d.get("moneyScale")),
                number(d.get("subtotalMinor")), number(d.get("travelFeeMinor")), number(d.get("taxMinor")),
                Boolean.TRUE.equals(d.get("taxIncluded")), number(d.get("tipMinor")), number(d.get("totalMinor")),
                number(d.get("platformFeeMinor")), (String) d.get("categoryName"), (String) d.get("addressText"),
                lines(d));
        receipts.insert(receipt);

        Map<String, Object> data = new HashMap<>();
        data.put("requestId", requestId.toString());
        data.put("receiptId", receipt.id().toString());
        data.put("receiptNumber", number);
        data.put("receiptType", type);
        data.put("totalMinor", receipt.totalMinor());
        data.put("currency", receipt.currency());
        data.put("issuedAt", issuedAt.toString());
        outbox.publish(EventTypes.BILLING_RECEIPT_ISSUED, event, requestId.toString(), data);
        log.info("Receipt {} ({}) issued for request {}", number, type, requestId);
    }

    /** Líneas del pedido; a cada impuesto se le agrega su tasa (puntos básicos) del perfil regional. */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lines(Map<String, Object> d) {
        Map<String, Object> rates = d.get("taxRates") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        List<Map<String, Object>> out = new ArrayList<>();
        if (d.get("lines") instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> line = new LinkedHashMap<>((Map<String, Object>) item);
                if ("TAX".equals(line.get("kind")) && rates.containsKey(String.valueOf(line.get("description")))) {
                    line.put("rateBps", rates.get(String.valueOf(line.get("description"))));
                }
                out.add(line);
            }
        }
        return out;
    }

    private static long number(Object value) {
        return value instanceof Number n ? n.longValue() : value == null ? 0 : Long.parseLong(value.toString());
    }

    private static UUID uuid(Object value) {
        return value == null ? null : UUID.fromString(value.toString());
    }

    private static String text(Object value, String fallback) {
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }
}
