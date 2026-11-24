package jornal.payments;

import jornal.common.events.CloudEvent;
import jornal.common.events.EventTypes;
import jornal.common.subsystem.SubsystemOutbox;
import jornal.payments.gateway.PaymentGateway;
import jornal.payments.gateway.PaymentGateway.GatewayResult;
import jornal.payments.gateway.PaymentGateway.PaymentOrder;
import jornal.payments.gateway.Simulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lógica de pagos. Cada método corre en la transacción del mensaje entrante: el cambio en {@code payments},
 * el libro y el evento de respuesta (en el outbox) se confirman juntos.
 *
 * <p>Liquidación al capturar: el trabajador recibe el subtotal menos la comisión ({@code commissionBps})
 * más el 100 % de la propina; la plataforma se queda con la comisión, el desplazamiento y el impuesto cobrado.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository payments;
    private final Map<String, PaymentGateway> gateways;
    private final SubsystemOutbox outbox;
    private final Simulator simulator;
    private final JsonMapper json;

    public PaymentService(PaymentRepository payments, List<PaymentGateway> gateways, SubsystemOutbox outbox,
                          Simulator simulator, JsonMapper json) {
        this.payments = payments;
        this.gateways = gateways.stream().collect(Collectors.toMap(PaymentGateway::flow, Function.identity()));
        this.outbox = outbox;
        this.simulator = simulator;
        this.json = json;
    }

    // ------------------------------------------------------------------ pedido creado o recotizado

    @Transactional
    public void onPaymentRequested(CloudEvent event) {
        Map<String, Object> d = event.data();
        UUID requestId = uuid(d.get("requestId"));
        int attempt = integer(d.get("attempt"));

        // Recotización: los intentos anteriores se anulan o reembolsan antes de cobrar el nuevo.
        for (Payment old : payments.findByRequest(event.region(), requestId)) {
            if (old.attempt() < attempt) compensate(old, event, "REPRICED");
        }

        Payment payment = new Payment(UUID.randomUUID(), event.region(), requestId, attempt,
                uuid(d.get("clientId")), uuidOrNull(d.get("workerId")), String.valueOf(d.get("paymentMethod")),
                String.valueOf(d.get("paymentFlow")), String.valueOf(d.get("currency")), number(d.get("totalMinor")),
                Payment.NEW, null, null, null, null, json.writeValueAsString(d), null, null);
        if (!payments.insertIfAbsent(payment)) {
            log.info("Payment for request {} attempt {} already exists: not charging again", requestId, attempt);
            return;
        }

        GatewayResult result = gateway(payment).start(new PaymentOrder(payment.id(), payment.region(),
                payment.method(), payment.currency(), payment.amountMinor(), payment.id().toString()));

        switch (result.outcome()) {
            case DECLINED -> {
                payments.transition(payment.region(), payment.id(), Payment.NEW, Payment.FAILED, null, result.reason());
                reply(EventTypes.PAYMENT_FAILED, event, payment, Map.of("reason", result.reason()));
                failed(payment, result.reason());
            }
            case PENDING -> {
                payments.transition(payment.region(), payment.id(), Payment.NEW, Payment.PENDING_VOUCHER,
                        result.providerRef(), null);
                payments.setVoucher(payment.region(), payment.id(), result.voucherCode(),
                        Instant.now().plus(simulator.settings().voucherExpireAfter()));
                log.info("Voucher {} issued for request {}", result.voucherCode(), requestId);
            }
            case APPROVED -> {
                boolean authOnly = "AUTH_CAPTURE".equals(payment.flow());
                payments.transition(payment.region(), payment.id(), Payment.NEW,
                        authOnly ? Payment.AUTHORIZED : Payment.CHARGED, result.providerRef(), null);
                if (!authOnly) payments.ledger(payment, "CHARGE", payment.amountMinor());
                reply(EventTypes.PAYMENT_AUTHORIZED, event, payment, Map.of());
            }
        }
    }

    // ------------------------------------------------------------------ voucher pagado (webhook o simulador)

    @Transactional
    public boolean voucherPaid(UUID paymentId) {
        Payment p = payments.findById(paymentId).orElse(null);
        if (p == null || !payments.transition(p.region(), p.id(), Payment.PENDING_VOUCHER, Payment.CHARGED, null, null)) {
            return false;
        }
        payments.ledger(p, "CHARGE", p.amountMinor());
        reply(EventTypes.PAYMENT_AUTHORIZED, p, Map.of());
        return true;
    }

    @Transactional
    public void voucherExpired(Payment p) {
        if (payments.transition(p.region(), p.id(), Payment.PENDING_VOUCHER, Payment.FAILED, null, "VOUCHER_EXPIRED")) {
            reply(EventTypes.PAYMENT_FAILED, p, Map.of("reason", "VOUCHER_EXPIRED"));
            failed(p, "VOUCHER_EXPIRED");
        }
    }

    // ------------------------------------------------------------------ captura

    @Transactional
    public void onCaptureRequested(CloudEvent event) {
        Map<String, Object> d = event.data();
        UUID requestId = uuid(d.get("requestId"));
        Payment p = payments.find(event.region(), requestId, integer(d.get("attempt"))).orElse(null);
        if (p == null) {
            log.warn("Capture requested for unknown payment of request {}", requestId);
            return;
        }
        payments.setWorkerAndData(p.region(), p.id(), uuidOrNull(d.get("workerId")), json.writeValueAsString(d));

        switch (p.status()) {
            case Payment.AUTHORIZED -> {
                GatewayResult r = gateway(p).capture(p.providerRef(), p.amountMinor());
                payments.transition(p.region(), p.id(), Payment.AUTHORIZED, Payment.CAPTURED, null, null);
                payments.ledger(p, "CAPTURE", p.amountMinor());
                log.debug("Captured {}", r.providerRef());
            }
            case Payment.CHARGED -> payments.transition(p.region(), p.id(), Payment.CHARGED, Payment.CAPTURED, null, null);
            case Payment.CAPTURED -> {
                log.info("Payment of request {} already captured", requestId);
                return;
            }
            default -> {
                log.warn("Cannot capture payment {} in {}", p.id(), p.status());
                return;
            }
        }
        Map<String, Object> settlement = settle(p, d);
        Map<String, Object> data = new HashMap<>(d);
        data.putAll(settlement);
        reply(EventTypes.PAYMENT_CAPTURED, event, p, data);
    }

    // ------------------------------------------------------------------ compensación

    @Transactional
    public void onCancelled(CloudEvent event) {
        Map<String, Object> d = event.data();
        if (Boolean.TRUE.equals(d.get("releaseOnly"))) return;   // solo libera la reserva del trabajador
        UUID requestId = uuid(d.get("requestId"));
        for (Payment p : payments.findByRequest(event.region(), requestId)) {
            compensate(p, event, String.valueOf(d.get("reason")));
        }
    }

    private void compensate(Payment p, CloudEvent cause, String reason) {
        switch (p.status()) {
            case Payment.AUTHORIZED -> {
                gateway(p).cancel(p.providerRef());
                if (payments.transition(p.region(), p.id(), Payment.AUTHORIZED, Payment.VOIDED, null, reason)) {
                    reply(EventTypes.PAYMENT_VOIDED, cause, p, Map.of("reason", reason));
                }
            }
            case Payment.CHARGED, Payment.CAPTURED -> {
                GatewayResult r = gateway(p).refund(p.providerRef(), p.amountMinor());
                if (payments.transition(p.region(), p.id(), p.status(), Payment.REFUNDED, null, reason)) {
                    payments.ledger(p, "REFUND", -p.amountMinor());
                    reply(EventTypes.PAYMENT_REFUNDED, cause, p, Map.of("reason", reason, "refundRef", r.providerRef()));
                }
            }
            case Payment.PENDING_VOUCHER -> {
                gateway(p).cancel(p.providerRef());
                payments.transition(p.region(), p.id(), Payment.PENDING_VOUCHER, Payment.CANCELLED, null, reason);
            }
            default -> log.debug("Nothing to compensate for payment {} in {}", p.id(), p.status());
        }
    }

    // ------------------------------------------------------------------ internos

    /** Comisión sobre el subtotal; el trabajador recibe subtotal − comisión + propina. */
    private Map<String, Object> settle(Payment p, Map<String, Object> d) {
        long subtotal = number(d.get("subtotalMinor"));
        long tip = number(d.get("tipMinor"));
        int bps = integer(d.getOrDefault("commissionBps", 0));
        long commission = BigDecimal.valueOf(subtotal).multiply(BigDecimal.valueOf(bps))
                .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP).longValueExact();
        long workerPayout = subtotal - commission;
        long platform = p.amountMinor() - workerPayout - tip;

        payments.ledger(p, "WORKER_PAYOUT", workerPayout);
        if (tip > 0) payments.ledger(p, "TIP_PAYOUT", tip);
        payments.ledger(p, "PLATFORM_FEE", platform);
        return Map.of("paymentId", p.id().toString(), "capturedMinor", p.amountMinor(),
                "commissionMinor", commission, "workerPayoutMinor", workerPayout,
                "tipPayoutMinor", tip, "platformFeeMinor", platform);
    }

    /** Métrica de fallas de pago por región, medio y motivo. */
    private static void failed(Payment p, String reason) {
        io.micrometer.core.instrument.Metrics.counter("jornal.payments.failures", "region", p.region(),
                "method", p.method(), "reason", String.valueOf(reason)).increment();
    }

    private PaymentGateway gateway(Payment p) {
        PaymentGateway gateway = gateways.get(p.flow());
        if (gateway == null) throw new IllegalStateException("No payment gateway for flow " + p.flow());
        return gateway;
    }

    private void reply(String type, CloudEvent cause, Payment p, Map<String, Object> extra) {
        outbox.publish(type, cause, p.requestId().toString(), data(p, extra));
    }

    private void reply(String type, Payment p, Map<String, Object> extra) {
        outbox.publish(type, p.region(), p.requestId().toString(), p.requestId().toString(), data(p, extra));
    }

    private static Map<String, Object> data(Payment p, Map<String, Object> extra) {
        Map<String, Object> data = new HashMap<>(extra);
        data.put("requestId", p.requestId().toString());
        data.put("attempt", p.attempt());
        data.put("paymentId", p.id().toString());
        data.put("method", p.method());
        data.put("flow", p.flow());
        data.put("amountMinor", p.amountMinor());
        data.put("currency", p.currency());
        return data;
    }

    private static UUID uuid(Object v) {
        return UUID.fromString(String.valueOf(v));
    }

    private static UUID uuidOrNull(Object v) {
        return v == null ? null : UUID.fromString(v.toString());
    }

    private static long number(Object v) {
        return v == null ? 0 : ((Number) v).longValue();
    }

    private static int integer(Object v) {
        return v == null ? 1 : ((Number) v).intValue();
    }
}
