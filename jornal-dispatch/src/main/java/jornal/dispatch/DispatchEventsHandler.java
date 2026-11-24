package jornal.dispatch;

import jornal.common.events.CloudEvent;
import jornal.common.events.EventTypes;
import jornal.common.subsystem.CloudEventHandler;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Eventos del core para asignación (cola {@code dispatch.<región>}). */
@Component
public class DispatchEventsHandler implements CloudEventHandler {

    private static final Set<String> TYPES = Set.of(EventTypes.WORKER_UPDATED, EventTypes.REQUEST_ASSIGNMENT_REQUESTED,
            EventTypes.REQUEST_CANCELLED, EventTypes.REQUEST_STATUS_CHANGED);

    private final DispatchService dispatch;

    public DispatchEventsHandler(DispatchService dispatch) {
        this.dispatch = dispatch;
    }

    @Override
    public boolean supports(String type) {
        return TYPES.contains(type);
    }

    @Override
    public void handle(CloudEvent event) {
        switch (event.type()) {
            case EventTypes.WORKER_UPDATED -> dispatch.onWorkerUpdated(event);
            case EventTypes.REQUEST_ASSIGNMENT_REQUESTED -> dispatch.onAssignmentRequested(event);
            case EventTypes.REQUEST_CANCELLED -> dispatch.onCancelled(event);
            case EventTypes.REQUEST_STATUS_CHANGED -> dispatch.onStatusChanged(event);
            default -> { }
        }
    }
}
