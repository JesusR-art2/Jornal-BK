package jornal.payments;

import jornal.common.events.CloudEvent;
import jornal.common.events.EventTypes;
import jornal.common.messaging.Topology;
import jornal.payments.gateway.Simulator;
import jornal.payments.gateway.SimulatorSettings;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F5: los tres flujos de pago con el proveedor simulado. La prueba hace de core: publica los comandos del pedido
 * y lee las respuestas en la cola {@code core.<región>}. Datos ficticios.
 */
@SpringBootTest(properties = "jornal.payments.simulator.voucher-auto-pay-after=0s")
@Import(PaymentsTestContainers.class)
class PaymentFlowsTest {

    private static final ParameterizedTypeReference<CloudEvent> EVENT = new ParameterizedTypeReference<>() { };

    @Autowired RabbitTemplate rabbit;
    @Autowired PaymentRepository payments;
    @Autowired PaymentService service;
    @Autowired VoucherJobs vouchers;
    @Autowired Simulator simulator;

    @Test
    void cardAuthorizesThenCapturesWithSettlement() {
        UUID request = UUID.randomUUID();
        send(EventTypes.REQUEST_CREATED, "co", request, order("CARD", "AUTH_CAPTURE", 1, 195000));

        CloudEvent authorized = await("core.co", EventTypes.PAYMENT_AUTHORIZED, request);
        assertThat(authorized.data()).containsEntry("attempt", 1).containsEntry("amountMinor", 195000);
        assertThat(status(request, 1)).isEqualTo(Payment.AUTHORIZED);

        Map<String, Object> capture = order("CARD", "AUTH_CAPTURE", 1, 195000);
        capture.put("workerId", UUID.randomUUID().toString());
        send(EventTypes.REQUEST_CAPTURE_REQUESTED, "co", request, capture);

        CloudEvent captured = await("core.co", EventTypes.PAYMENT_CAPTURED, request);
        // subtotal 170000, comisión 10 % = 17000, trabajador 153000 + propina 17000; plataforma 8000 + 17000
        assertThat(captured.data())
                .containsEntry("commissionMinor", 17000)
                .containsEntry("workerPayoutMinor", 153000)
                .containsEntry("tipPayoutMinor", 17000)
                .containsEntry("platformFeeMinor", 25000)
                .containsKey("lines");
        await("billing.co", EventTypes.PAYMENT_CAPTURED, request);   // facturación también lo recibe
        assertThat(status(request, 1)).isEqualTo(Payment.CAPTURED);
        assertThat(ledgerKinds(request)).containsExactlyInAnyOrder("CAPTURE", "WORKER_PAYOUT", "TIP_PAYOUT", "PLATFORM_FEE");
    }

    @Test
    void duplicatedOrderIsNotChargedTwice() {
        UUID request = UUID.randomUUID();
        Map<String, Object> data = order("PSE", "CHARGE_REFUND", 1, 100000);
        send(EventTypes.REQUEST_CREATED, "co", request, data);   // dos mensajes distintos (ids distintos)
        send(EventTypes.REQUEST_CREATED, "co", request, data);   // para el mismo pedido e intento

        await("core.co", EventTypes.PAYMENT_AUTHORIZED, request);
        assertThat(maybe("core.co", EventTypes.PAYMENT_AUTHORIZED, request, 2000)).isNull();
        assertThat(payments.findByRequestId(request)).hasSize(1);
        assertThat(ledgerKinds(request)).containsExactly("CHARGE");
    }

    @Test
    void instantPaymentIsRefundedOnCancellation() {
        UUID request = UUID.randomUUID();
        send(EventTypes.REQUEST_CREATED, "co", request, order("NEQUI", "CHARGE_REFUND", 1, 120000));
        await("core.co", EventTypes.PAYMENT_AUTHORIZED, request);
        assertThat(status(request, 1)).isEqualTo(Payment.CHARGED);

        send(EventTypes.REQUEST_CANCELLED, "co", request, cancel(order("NEQUI", "CHARGE_REFUND", 1, 120000)));
        CloudEvent refunded = await("core.co", EventTypes.PAYMENT_REFUNDED, request);
        assertThat(refunded.data()).containsEntry("reason", "NO_WORKER_AVAILABLE");
        assertThat(status(request, 1)).isEqualTo(Payment.REFUNDED);
        assertThat(ledgerKinds(request)).containsExactlyInAnyOrder("CHARGE", "REFUND");
    }

    @Test
    void cardAuthorizationIsVoidedOnCancellation() {
        UUID request = UUID.randomUUID();
        send(EventTypes.REQUEST_CREATED, "us", request, order("CARD", "AUTH_CAPTURE", 1, 37640));
        await("core.us", EventTypes.PAYMENT_AUTHORIZED, request);

        send(EventTypes.REQUEST_CANCELLED, "us", request, cancel(order("CARD", "AUTH_CAPTURE", 1, 37640)));
        await("core.us", EventTypes.PAYMENT_VOIDED, request);
        assertThat(payments.find("us", request, 1)).get().extracting(Payment::status).isEqualTo(Payment.VOIDED);
        assertThat(ledgerKinds(request)).isEmpty();      // nunca se cobró
    }

    @Test
    void voucherWaitsForTheWebhookAndExpires() {
        UUID paid = UUID.randomUUID();
        send(EventTypes.REQUEST_CREATED, "br", paid, order("BOLETO", "ASYNC_VOUCHER", 1, 38400));
        Payment voucher = waitFor(() -> payments.find("br", paid, 1)
                .filter(p -> Payment.PENDING_VOUCHER.equals(p.status())).orElse(null));
        assertThat(voucher.voucherCode()).hasSize(12);
        assertThat(maybe("core.br", EventTypes.PAYMENT_AUTHORIZED, paid, 1500)).isNull();   // aún no pagado

        assertThat(service.voucherPaid(voucher.id())).isTrue();       // webhook del proveedor
        assertThat(service.voucherPaid(voucher.id())).isFalse();      // un segundo aviso no cobra otra vez
        await("core.br", EventTypes.PAYMENT_AUTHORIZED, paid);

        UUID unpaid = UUID.randomUUID();
        send(EventTypes.REQUEST_CREATED, "br", unpaid, order("BOLETO", "ASYNC_VOUCHER", 1, 38400));
        waitFor(() -> payments.find("br", unpaid, 1).filter(p -> Payment.PENDING_VOUCHER.equals(p.status())).orElse(null));
        assertThat(vouchers.expire(Instant.now().plus(Duration.ofMinutes(5)))).isPositive();
        CloudEvent failed = await("core.br", EventTypes.PAYMENT_FAILED, unpaid);
        assertThat(failed.data()).containsEntry("reason", "VOUCHER_EXPIRED");
    }

    @Test
    void repricingVoidsThePreviousAttempt() {
        UUID request = UUID.randomUUID();
        send(EventTypes.REQUEST_CREATED, "co", request, order("CARD", "AUTH_CAPTURE", 1, 195000));
        await("core.co", EventTypes.PAYMENT_AUTHORIZED, request);

        send(EventTypes.REQUEST_REPRICED, "co", request, order("CARD", "AUTH_CAPTURE", 2, 280000));
        CloudEvent voided = await("core.co", EventTypes.PAYMENT_VOIDED, request);
        assertThat(voided.data()).containsEntry("attempt", 1);
        CloudEvent authorized = await("core.co", EventTypes.PAYMENT_AUTHORIZED, request);
        assertThat(authorized.data()).containsEntry("attempt", 2).containsEntry("amountMinor", 280000);
    }

    @Test
    void simulatorCanDeclinePayments() {
        SimulatorSettings original = simulator.settings();
        try {
            simulator.update(new SimulatorSettings(0, List.of("PAYPAY"), Duration.ZERO, Duration.ZERO, Duration.ofSeconds(90)));
            UUID request = UUID.randomUUID();
            send(EventTypes.REQUEST_CREATED, "jp", request, order("PAYPAY", "CHARGE_REFUND", 1, 29000));
            CloudEvent failed = await("core.jp", EventTypes.PAYMENT_FAILED, request);
            assertThat(failed.data()).containsEntry("reason", "METHOD_DECLINED");
            assertThat(status("jp", request, 1)).isEqualTo(Payment.FAILED);
        } finally {
            simulator.update(original);
        }
    }

    // ------------------------------------------------------------------ utilidades

    /** Datos del pedido como los manda el core (pedido D4 en COP). */
    private static Map<String, Object> order(String method, String flow, int attempt, long total) {
        Map<String, Object> d = new HashMap<>();
        d.put("clientId", UUID.randomUUID().toString());
        d.put("attempt", attempt);
        d.put("paymentMethod", method);
        d.put("paymentFlow", flow);
        d.put("currency", "COP");
        d.put("subtotalMinor", 170000);
        d.put("travelFeeMinor", 8000);
        d.put("taxMinor", 28420);
        d.put("tipMinor", 17000);
        d.put("totalMinor", total);
        d.put("commissionBps", 1000);
        d.put("lines", List.of(Map.of("kind", "SERVICE", "amountMinor", 170000)));
        return d;
    }

    private static Map<String, Object> cancel(Map<String, Object> data) {
        data.put("reason", "NO_WORKER_AVAILABLE");
        data.put("paymentAuthorized", true);
        return data;
    }

    private void send(String type, String region, UUID request, Map<String, Object> data) {
        Map<String, Object> payload = new HashMap<>(data);
        payload.put("requestId", request.toString());
        CloudEvent e = CloudEvent.of("jornal-core-" + region, type, region, request.toString(), 1, payload,
                Instant.now(), request.toString());
        rabbit.convertAndSend(Topology.EXCHANGE, e.routingKey(), e);
    }

    private CloudEvent await(String queue, String type, UUID request) {
        CloudEvent e = maybe(queue, type, request, 15_000);
        assertThat(e).as("%s for %s in %s", type, request, queue).isNotNull();
        return e;
    }

    private CloudEvent maybe(String queue, String type, UUID request, long millis) {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            CloudEvent e = rabbit.receiveAndConvert(queue, 300, EVENT);
            if (e != null && type.equals(e.type()) && request.toString().equals(e.subject())) return e;
        }
        return null;
    }

    private String status(UUID request, int attempt) {
        return status("co", request, attempt);
    }

    private String status(String region, UUID request, int attempt) {
        return payments.find(region, request, attempt).map(Payment::status).orElse(null);
    }

    private List<String> ledgerKinds(UUID request) {
        return payments.ledgerOf(request).stream().map(r -> (String) r.get("kind")).toList();
    }

    private static <T> T waitFor(Supplier<T> supplier) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            T value = supplier.get();
            if (value != null) return value;
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        throw new AssertionError("condition not met in 15 s");
    }
}
