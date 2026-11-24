package com.jornal.integration;

import com.jornal.region.RegionApi;
import com.jornal.region.RegionProfileUpdated;
import com.jornal.region.RegionProfileView;
import jornal.common.events.CloudEvent;
import jornal.common.events.EventTypes;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;

/**
 * Traduce eventos internos a mensajes de integración. Corre dentro de la transacción del cambio, así el
 * mensaje queda en el Outbox junto con el dato (o no queda ninguno de los dos).
 */
@Component
@RequiredArgsConstructor
public class RegionEventsPublisher {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private final RegionApi regions;
    private final ApplicationEventPublisher events;
    private final JsonMapper json;
    private final Clock clock;

    @EventListener
    public void on(RegionProfileUpdated updated) {
        RegionProfileView profile = regions.find(updated.code()).orElseThrow();
        Map<String, Object> data = json.convertValue(profile, MAP);
        events.publishEvent(CloudEvent.of(source(), EventTypes.REGION_PROFILE_UPDATED,
                updated.code(), updated.code(), updated.version(), data, clock.instant(), null));
    }

    private String source() {
        return "jornal-core-" + regions.currentCode().toLowerCase(Locale.ROOT);
    }
}
