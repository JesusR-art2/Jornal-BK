package com.jornal.shared;

import jornal.common.events.CloudEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;

/**
 * Publica mensajes de integración ({@link CloudEvent}). Se guardan en el Outbox dentro de la transacción
 * actual y salen a RabbitMQ después del commit (ver {@code integration.IntegrationConfig}).
 */
@Component
public class IntegrationEvents {

    private final ApplicationEventPublisher publisher;
    private final Clock clock;
    private final String region;

    public IntegrationEvents(ApplicationEventPublisher publisher, Clock clock,
                             @Value("${jornal.region.code}") String region) {
        this.publisher = publisher;
        this.clock = clock;
        this.region = region.toLowerCase(Locale.ROOT);
    }

    public CloudEvent publish(String type, String subject, long aggregateVersion,
                              Map<String, Object> data, String correlationId) {
        CloudEvent event = CloudEvent.of("jornal-core-" + region, type, region, subject, aggregateVersion,
                data, clock.instant(), jornal.common.observability.Correlation.currentOr(correlationId));
        publisher.publishEvent(event);
        return event;
    }
}
