package jornal.payments;

import java.time.Instant;
import java.util.UUID;

/**
 * Un intento de pago de un pedido. Estados:
 * AUTHORIZED (retenido) · CHARGED (cobrado, falta confirmar el trabajo) · PENDING_VOUCHER · CAPTURED (cobro final)
 * · VOIDED (retención anulada) · REFUNDED · CANCELLED (voucher anulado) · FAILED.
 */
public record Payment(UUID id, String region, UUID requestId, int attempt, UUID clientId, UUID workerId,
                      String method, String flow, String currency, long amountMinor, String status,
                      String providerRef, String voucherCode, Instant voucherExpiresAt, String failureReason,
                      String requestData, Instant createdAt, Instant updatedAt) {

    public static final String AUTHORIZED = "AUTHORIZED";
    public static final String CHARGED = "CHARGED";
    public static final String PENDING_VOUCHER = "PENDING_VOUCHER";
    public static final String CAPTURED = "CAPTURED";
    public static final String VOIDED = "VOIDED";
    public static final String REFUNDED = "REFUNDED";
    public static final String CANCELLED = "CANCELLED";
    public static final String FAILED = "FAILED";
    public static final String NEW = "NEW";
}
