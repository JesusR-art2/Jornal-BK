package jornal.notifications;

import jornal.common.events.CloudEvent;
import jornal.common.messaging.Topology;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Posición en vivo del trabajador: llega por el exchange no durable {@code jornal.live} (cola temporal propia
 * de esta instancia) y sale por el tema del pedido. Si se pierde una posición no pasa nada: gana la siguiente.
 */
@Component
public class LiveForwarder {

    private final SimpMessagingTemplate websocket;

    public LiveForwarder(SimpMessagingTemplate websocket) {
        this.websocket = websocket;
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(durable = "false", exclusive = "true", autoDelete = "true"),
            exchange = @Exchange(value = Topology.LIVE_EXCHANGE, type = ExchangeTypes.TOPIC, durable = "false"),
            key = "*.worker-location"))
    public void onLocation(CloudEvent event) {
        Object requestId = event.data().get("requestId");
        if (requestId == null) {
            return;   // el trabajador no tiene pedido reservado: nadie lo sigue
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "location");
        payload.put("requestId", requestId.toString());
        payload.put("latitude", event.data().get("latitude"));
        payload.put("longitude", event.data().get("longitude"));
        payload.put("at", event.data().get("at"));
        websocket.convertAndSend("/topic/requests." + requestId, (Object) payload);
    }
}
