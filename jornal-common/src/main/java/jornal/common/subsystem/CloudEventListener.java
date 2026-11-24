package jornal.common.subsystem;

import io.micrometer.core.instrument.MeterRegistry;
import jornal.common.events.CloudEvent;
import jornal.common.observability.Correlation;
import jornal.common.observability.MessageMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Consume las colas del subsistema en todas las regiones. Primero registra el mensaje en el inbox
 * (si ya estaba, lo descarta) y luego lo entrega a los {@link CloudEventHandler} que lo soportan,
 * todo en una transacción local. Si un handler falla, el mensaje se reintenta y, agotados los
 * reintentos, va a la cola de mensajes muertos. El {@code correlationid} del mensaje queda en cada línea de log.
 */
@Component
public class CloudEventListener {

    private static final Logger log = LoggerFactory.getLogger(CloudEventListener.class);

    private final Inbox inbox;
    private final List<CloudEventHandler> handlers;
    private final SubsystemProperties subsystem;
    private final MeterRegistry metrics;

    public CloudEventListener(Inbox inbox, List<CloudEventHandler> handlers, SubsystemProperties subsystem,
                              ObjectProvider<MeterRegistry> metrics) {
        this.inbox = inbox;
        this.handlers = handlers;
        this.subsystem = subsystem;
        this.metrics = metrics.getIfAvailable();
    }

    @RabbitListener(queues = "#{subsystemQueueNames}")
    @Transactional
    public void onEvent(CloudEvent event) {
        try (MDC.MDCCloseable ignored = Correlation.open(event.correlationid())) {
            if (!inbox.firstTime(event)) {
                log.info("Duplicate message {} ({}) discarded", event.id(), event.type());
                MessageMetrics.record(metrics, subsystem.name(), event, "duplicate");
                return;
            }
            int handled = 0;
            try {
                for (CloudEventHandler handler : handlers) {
                    if (handler.supports(event.type())) {
                        handler.handle(event);
                        handled++;
                    }
                }
            } catch (RuntimeException e) {
                MessageMetrics.record(metrics, subsystem.name(), event, "error");
                throw e;
            }
            MessageMetrics.record(metrics, subsystem.name(), event, "ok");
            log.info("Processed {} [{}] region={} subject={} handlers={}",
                    event.id(), event.type(), event.region(), event.subject(), handled);
        }
    }
}
