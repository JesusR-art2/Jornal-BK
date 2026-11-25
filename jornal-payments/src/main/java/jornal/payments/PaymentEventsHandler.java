package jornal.payments;

import jornal.common.events.CloudEvent;
import jornal.common.events.EventTypes;
import jornal.common.subsystem.CloudEventHandler;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Comandos del core para pagos (cola {@code payments.<región>}). */
@Component
public class PaymentEventsHandler implements CloudEventHandler {

    private static final Set<String> TYPES = Set.of(EventTypes.REQUEST_CREATED, EventTypes.REQUEST_REPRICED,
            EventTypes.REQUEST_CAPTURE_REQUESTED, EventTypes.REQUEST_CANCELLED, EventTypes.VACANCY_PAYOUT_REQUESTED);

    private final PaymentService payments;
    private final PayoutService payouts;

    public PaymentEventsHandler(PaymentService payments, PayoutService payouts) {
        this.payouts = payouts;
        this.payments = payments;
    }

    @Override
    public boolean supports(String type) {
        return TYPES.contains(type);
    }

    @Override
    public void handle(CloudEvent event) {
        switch (event.type()) {
            case EventTypes.REQUEST_CREATED, EventTypes.REQUEST_REPRICED -> payments.onPaymentRequested(event);
            case EventTypes.REQUEST_CAPTURE_REQUESTED -> payments.onCaptureRequested(event);
            case EventTypes.REQUEST_CANCELLED -> payments.onCancelled(event);
            case EventTypes.VACANCY_PAYOUT_REQUESTED -> payouts.onPayoutRequested(event);
            default -> { }
        }
    }
}
