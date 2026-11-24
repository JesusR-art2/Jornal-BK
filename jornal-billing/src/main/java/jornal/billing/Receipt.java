package jornal.billing;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Comprobante emitido. Montos en unidades menores de la moneda local; {@code lines} con la tasa de cada impuesto. */
public record Receipt(
        UUID id, String region, UUID requestId, String receiptType, long sequence, String number,
        UUID clientId, UUID workerId, String issuerName, String issuerTaxId,
        String customerName, String customerLegalName, String customerTaxIdType, String customerTaxIdNumber,
        String locale, String timezone, Instant issuedAt, String issuedLocal,
        String currency, int moneyScale, long subtotalMinor, long travelFeeMinor, long taxMinor, boolean taxIncluded,
        long tipMinor, long totalMinor, long platformRevenueMinor, String categoryName, String addressText,
        List<Map<String, Object>> lines) {
}
