package jornal.common.subsystem;

import jornal.common.events.CloudEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Consume las colas del subsistema en todas las regiones. Primero registra el mensaje en el inbox
 * (si ya estaba, lo descarta) y luego lo entrega a los {@link CloudEventHandler} que lo soportan,
 * todo en una transacción local. Si un handler falla, el mensaje se reintenta y, agotados los
 * reintentos, va a la cola de mensajes muertos.
 */
@Component
public class CloudEventListener {

    private static final Logger log = LoggerFactory.getLogger(CloudEventListener.class);

    private final Inbox inbox;
    private final List<CloudEventHandler> handlers;

    public CloudEventListener(Inbox inbox, List<CloudEventHandler> handlers) {
        this.inbox = inbox;
        this.handlers = handlers;
    }

    @RabbitListener(queues = "#{subsystemQueueNames}")
    @Transactional
    public void onEvent(CloudEvent event) {
        if (!inbox.firstTime(event)) {
            log.info("Duplicate message {} ({}) discarded", event.id(), event.type());
            return;
        }
        int handled = 0;
        for (CloudEventHandler handler : handlers) {
            if (handler.supports(event.type())) {
                handler.handle(event);
                handled++;
            }
        }
        log.info("Processed {} [{}] region={} subject={} handlers={}",
                event.id(), event.type(), event.region(), event.subject(), handled);
    }
}
