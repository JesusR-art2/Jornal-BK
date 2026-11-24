package jornal.payments.gateway;

import java.util.UUID;

/**
 * Puerto hacia un proveedor de pagos. Hay un adaptador por flujo ({@link #flow()}) y el perfil regional elige
 * el flujo de cada medio de pago ({@code paymentMethods[].flow}). En el prototipo los adaptadores son simulados;
 * uno real (Wompi, PayU, Mercado Pago, Stripe, PayPay...) implementa esta misma interfaz sin tocar la lógica.
 *
 * <ul>
 *   <li>{@code AUTH_CAPTURE} (tarjeta): {@link #start} autoriza (retiene), {@link #capture} cobra al confirmar,
 *       {@link #cancel} anula la retención.</li>
 *   <li>{@code CHARGE_REFUND} (PSE, Nequi, Pix, PayPay): {@link #start} cobra de inmediato; la compensación
 *       es {@link #refund}.</li>
 *   <li>{@code ASYNC_VOUCHER} (boleto, konbini): {@link #start} emite un voucher (PENDING); el proveedor avisa
 *       el pago por webhook; {@link #cancel} anula un voucher sin pagar.</li>
 * </ul>
 *
 * Todas las llamadas llevan una clave de idempotencia ({@link PaymentOrder#idempotencyKey()}): repetirlas no
 * vuelve a cobrar.
 */
public interface PaymentGateway {

    String flow();

    GatewayResult start(PaymentOrder order);

    GatewayResult capture(String providerRef, long amountMinor);

    GatewayResult cancel(String providerRef);

    GatewayResult refund(String providerRef, long amountMinor);

    record PaymentOrder(UUID paymentId, String region, String method, String currency, long amountMinor,
                        String idempotencyKey) {
    }

    enum Outcome { APPROVED, PENDING, DECLINED }

    record GatewayResult(Outcome outcome, String providerRef, String reason, String voucherCode) {

        public static GatewayResult approved(String ref) {
            return new GatewayResult(Outcome.APPROVED, ref, null, null);
        }

        public static GatewayResult pending(String ref, String voucherCode) {
            return new GatewayResult(Outcome.PENDING, ref, null, voucherCode);
        }

        public static GatewayResult declined(String reason) {
            return new GatewayResult(Outcome.DECLINED, null, reason, null);
        }

        public boolean approved() {
            return outcome == Outcome.APPROVED;
        }
    }
}
