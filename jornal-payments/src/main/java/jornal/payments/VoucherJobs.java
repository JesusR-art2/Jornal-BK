package jornal.payments;

import jornal.payments.gateway.Simulator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Vouchers (boleto, konbini): el simulador los da por pagados tras {@code voucherAutoPayAfter} (como si llegara
 * el webhook del proveedor) y los vence tras {@code voucherExpireAfter}. Los métodos con {@code now} sirven
 * para probarlos con cualquier hora.
 */
@Component
public class VoucherJobs {

    private final PaymentRepository payments;
    private final PaymentService service;
    private final Simulator simulator;

    public VoucherJobs(PaymentRepository payments, PaymentService service, Simulator simulator) {
        this.payments = payments;
        this.service = service;
        this.simulator = simulator;
    }

    @Scheduled(fixedDelayString = "${jornal.payments.voucher-check-every:2s}")
    public void run() {
        Instant now = Instant.now();
        autoPay(now);
        expire(now);
    }

    public int autoPay(Instant now) {
        Duration after = simulator.settings().voucherAutoPayAfter();
        if (after.isZero()) return 0;
        int paid = 0;
        for (Payment p : payments.pendingVouchersCreatedBefore(now.minus(after))) {
            if (service.voucherPaid(p.id())) paid++;
        }
        return paid;
    }

    public int expire(Instant now) {
        var expired = payments.expiredVouchers(now);
        expired.forEach(service::voucherExpired);
        return expired.size();
    }
}
