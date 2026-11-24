package jornal.billing;

import jornal.common.events.CloudEvent;
import jornal.common.events.EventTypes;
import jornal.common.subsystem.CloudEventHandler;
import org.springframework.stereotype.Component;

/** Eventos de pagos para facturación (cola {@code billing.<región>}). */
@Component
public class BillingEventsHandler implements CloudEventHandler {

    private final BillingService billing;

    public BillingEventsHandler(BillingService billing) {
        this.billing = billing;
    }

    @Override
    public boolean supports(String type) {
        return EventTypes.PAYMENT_CAPTURED.equals(type);
    }

    @Override
    public void handle(CloudEvent event) {
        billing.onPaymentCaptured(event);
    }
}
