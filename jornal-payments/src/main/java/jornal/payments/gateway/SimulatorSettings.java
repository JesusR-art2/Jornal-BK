package jornal.payments.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Comportamiento del proveedor simulado, para las demostraciones. Se cambia en caliente con
 * {@code PUT /simulator} (por el gateway: {@code /subsystems/payments/simulator}).
 *
 * @param failureRate         probabilidad de rechazo al iniciar un pago (0 a 1)
 * @param declineMethods      medios que siempre se rechazan (por ejemplo {@code CARD})
 * @param latency             demora de cada llamada al proveedor
 * @param voucherAutoPayAfter el voucher se da por pagado solo tras este tiempo (0 = nunca, solo por webhook)
 * @param voucherExpireAfter  vencimiento del voucher sin pagar
 */
@ConfigurationProperties("jornal.payments.simulator")
public record SimulatorSettings(double failureRate, List<String> declineMethods, Duration latency,
                                Duration voucherAutoPayAfter, Duration voucherExpireAfter) {

    public SimulatorSettings {
        if (failureRate < 0 || failureRate > 1) throw new IllegalArgumentException("failureRate must be 0..1");
        declineMethods = declineMethods == null ? List.of()
                : declineMethods.stream().map(m -> m.toUpperCase(Locale.ROOT)).toList();
        latency = latency == null ? Duration.ZERO : latency;
        voucherAutoPayAfter = voucherAutoPayAfter == null ? Duration.ofSeconds(10) : voucherAutoPayAfter;
        voucherExpireAfter = voucherExpireAfter == null ? Duration.ofSeconds(90) : voucherExpireAfter;
    }

    /** Motivo de rechazo, o {@code null} si el pago se aprueba. */
    String declineReason(String method) {
        if (declineMethods.contains(method.toUpperCase(Locale.ROOT))) return "METHOD_DECLINED";
        if (failureRate > 0 && ThreadLocalRandom.current().nextDouble() < failureRate) return "DECLINED_BY_ISSUER";
        return null;
    }

    void simulateLatency() {
        if (latency.isZero()) return;
        try {
            Thread.sleep(latency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
