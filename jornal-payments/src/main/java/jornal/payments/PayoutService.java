package jornal.payments;

import jornal.common.events.CloudEvent;
import jornal.common.events.EventTypes;
import jornal.common.subsystem.SubsystemOutbox;
import jornal.payments.gateway.Simulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pago automático al trabajador de una vacante, pedido por el core cuando el empleador presiona «Finalizar»
 * con el pago automático activo. La transferencia es simulada; el método {@code PAYOUT} en
 * {@code declineMethods} del simulador la hace fallar (para la demo). Una sola transferencia por postulación:
 * un comando repetido responde con el resultado ya guardado.
 */
@Service
public class PayoutService {

    private static final Logger log = LoggerFactory.getLogger(PayoutService.class);
    static final String METHOD = "PAYOUT";

    private final JdbcTemplate jdbc;
    private final SubsystemOutbox outbox;
    private final Simulator simulator;
    private final Clock clock;

    public PayoutService(JdbcTemplate jdbc, SubsystemOutbox outbox, Simulator simulator, Clock clock) {
        this.jdbc = jdbc;
        this.outbox = outbox;
        this.simulator = simulator;
        this.clock = clock;
    }

    @Transactional
    public void onPayoutRequested(CloudEvent event) {
        Map<String, Object> d = event.data();
        UUID applicationId = UUID.fromString(String.valueOf(d.get("applicationId")));
        long amount = ((Number) d.get("amountMinor")).longValue();

        List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT status, provider_ref, failure_reason FROM payouts WHERE region = ? AND application_id = ?",
                event.region(), applicationId);
        String status;
        String ref;
        String reason;
        if (!existing.isEmpty()) {
            status = (String) existing.get(0).get("status");
            ref = (String) existing.get(0).get("provider_ref");
            reason = (String) existing.get(0).get("failure_reason");
        } else {
            boolean declined = simulator.settings().declineMethods().contains(METHOD) || amount <= 0;
            status = declined ? "FAILED" : "PAID";
            ref = declined ? null : "PO-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
            reason = declined ? "DECLINED" : null;
            jdbc.update("""
                    INSERT INTO payouts (id, region, application_id, vacancy_id, worker_id, employer_id, amount_minor,
                                         currency, status, provider_ref, failure_reason, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), event.region(), applicationId,
                    UUID.fromString(String.valueOf(d.get("vacancyId"))), UUID.fromString(String.valueOf(d.get("workerId"))),
                    UUID.fromString(String.valueOf(d.get("employerId"))), amount, String.valueOf(d.get("currency")),
                    status, ref, reason, Timestamp.from(clock.instant()));
            log.info("[PAYOUT simulated] application {}: {} {} {}", applicationId, amount, d.get("currency"), status);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("applicationId", applicationId.toString());
        data.put("vacancyId", String.valueOf(d.get("vacancyId")));
        data.put("amountMinor", amount);
        data.put("currency", String.valueOf(d.get("currency")));
        if (ref != null) data.put("providerRef", ref);
        if (reason != null) data.put("reason", reason);
        outbox.publish("PAID".equals(status) ? EventTypes.PAYMENT_PAYOUT_COMPLETED : EventTypes.PAYMENT_PAYOUT_FAILED,
                event, applicationId.toString(), data);
    }
}
