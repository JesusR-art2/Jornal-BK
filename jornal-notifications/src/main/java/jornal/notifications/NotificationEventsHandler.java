package jornal.notifications;

import jornal.common.events.CloudEvent;
import jornal.common.events.EventTypes;
import jornal.common.subsystem.CloudEventHandler;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Eventos que generan avisos (cola {@code notifications.<región>}, que recibe todos los eventos). */
@Component
public class NotificationEventsHandler implements CloudEventHandler {

    private static final Set<String> TYPES = Set.of(EventTypes.REQUEST_STATUS_CHANGED, EventTypes.DISPATCH_OFFER_CREATED);

    private final NotificationService notifications;

    public NotificationEventsHandler(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Override
    public boolean supports(String type) {
        return TYPES.contains(type);
    }

    @Override
    public void handle(CloudEvent event) {
        switch (event.type()) {
            case EventTypes.REQUEST_STATUS_CHANGED -> notifications.onStatusChanged(event);
            case EventTypes.DISPATCH_OFFER_CREATED -> notifications.onOfferCreated(event);
            default -> { }
        }
    }
}
