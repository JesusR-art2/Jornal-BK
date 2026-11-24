package jornal.common.subsystem;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Estado mínimo del subsistema, útil para demostraciones y pruebas de falla. */
@RestController
public class SubsystemStatusController {

    private final SubsystemProperties properties;
    private final Inbox inbox;
    private final RegionProfileCache profiles;
    private final String[] queues;

    public SubsystemStatusController(SubsystemProperties properties, Inbox inbox, RegionProfileCache profiles,
                                     String[] subsystemQueueNames) {
        this.properties = properties;
        this.inbox = inbox;
        this.profiles = profiles;
        this.queues = subsystemQueueNames;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("subsystem", properties.name(),
                "queues", List.of(queues),
                "processedMessages", inbox.count(),
                "regionProfiles", profiles.entries());
    }
}
