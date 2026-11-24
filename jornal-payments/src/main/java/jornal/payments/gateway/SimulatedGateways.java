package jornal.payments.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

/**
 * Adaptadores simulados, uno por flujo. Las referencias del proveedor se derivan de la clave de idempotencia,
 * así repetir una llamada devuelve el mismo resultado sin "cobrar" dos veces.
 */
@Configuration(proxyBeanMethods = false)
public class SimulatedGateways {

    @Bean
    PaymentGateway authCaptureGateway(Simulator simulator) {
        return new Base("AUTH_CAPTURE", "auth", simulator) {
            @Override
            GatewayResult approvedStart(PaymentOrder order) {
                return GatewayResult.approved(ref(order));
            }
        };
    }

    @Bean
    PaymentGateway chargeRefundGateway(Simulator simulator) {
        return new Base("CHARGE_REFUND", "chg", simulator) {
            @Override
            GatewayResult approvedStart(PaymentOrder order) {
                return GatewayResult.approved(ref(order));
            }
        };
    }

    @Bean
    PaymentGateway asyncVoucherGateway(Simulator simulator) {
        return new Base("ASYNC_VOUCHER", "vch", simulator) {
            @Override
            GatewayResult approvedStart(PaymentOrder order) {
                String code = order.paymentId().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
                return GatewayResult.pending(ref(order), code);
            }
        };
    }

    private abstract static class Base implements PaymentGateway {

        private final String flow;
        private final String prefix;
        private final Simulator simulator;

        Base(String flow, String prefix, Simulator simulator) {
            this.flow = flow;
            this.prefix = prefix;
            this.simulator = simulator;
        }

        abstract GatewayResult approvedStart(PaymentOrder order);

        String ref(PaymentOrder order) {
            return "sim-" + prefix + "-" + order.idempotencyKey();
        }

        @Override
        public String flow() {
            return flow;
        }

        @Override
        public GatewayResult start(PaymentOrder order) {
            SimulatorSettings s = simulator.settings();
            s.simulateLatency();
            String reason = s.declineReason(order.method());
            return reason != null ? GatewayResult.declined(reason) : approvedStart(order);
        }

        @Override
        public GatewayResult capture(String providerRef, long amountMinor) {
            simulator.settings().simulateLatency();
            return GatewayResult.approved(providerRef);
        }

        @Override
        public GatewayResult cancel(String providerRef) {
            simulator.settings().simulateLatency();
            return GatewayResult.approved(providerRef);
        }

        @Override
        public GatewayResult refund(String providerRef, long amountMinor) {
            simulator.settings().simulateLatency();
            return GatewayResult.approved(providerRef + "-refund");
        }
    }
}
