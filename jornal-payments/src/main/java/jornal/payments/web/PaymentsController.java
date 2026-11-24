package jornal.payments.web;

import jornal.payments.Payment;
import jornal.payments.PaymentRepository;
import jornal.payments.PaymentService;
import jornal.payments.gateway.Simulator;
import jornal.payments.gateway.SimulatorSettings;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * API interna del subsistema de pagos (en Docker: {@code /subsystems/payments/...} por el gateway).
 * Sin autenticación en el prototipo: en producción quedaría en la red interna o detrás del JWT (F2 P2).
 */
@RestController
public class PaymentsController {

    private final PaymentRepository payments;
    private final PaymentService service;
    private final Simulator simulator;

    public PaymentsController(PaymentRepository payments, PaymentService service, Simulator simulator) {
        this.payments = payments;
        this.service = service;
        this.simulator = simulator;
    }

    /** Intentos de pago de un pedido, con su libro de movimientos. */
    @GetMapping("/payments")
    public Map<String, Object> byRequest(@RequestParam UUID requestId) {
        List<Payment> list = payments.findByRequestId(requestId);
        return Map.of("payments", list, "ledger", payments.ledgerOf(requestId));
    }

    /** Totales por región, moneda y tipo: cobros, reembolsos, pagos a trabajadores, propinas y comisión. */
    @GetMapping("/ledger/summary")
    public List<Map<String, Object>> summary() {
        return payments.summary();
    }

    @GetMapping("/simulator")
    public SimulatorSettings simulator() {
        return simulator.settings();
    }

    /** Cambia el comportamiento del proveedor simulado sin reiniciar (demostraciones de falla). */
    @PutMapping("/simulator")
    public SimulatorSettings updateSimulator(@RequestBody SimulatorSettings settings) {
        return simulator.update(settings);
    }

    /** Webhook simulado del proveedor: el cliente pagó el voucher (boleto, konbini). */
    @PostMapping("/webhooks/vouchers/{paymentId}/paid")
    public ResponseEntity<Map<String, Object>> voucherPaid(@PathVariable UUID paymentId) {
        boolean applied = service.voucherPaid(paymentId);
        return applied ? ResponseEntity.ok(Map.of("paymentId", paymentId, "status", Payment.CHARGED))
                : ResponseEntity.status(409).body(Map.of("paymentId", paymentId, "status", "NOT_PENDING"));
    }
}
