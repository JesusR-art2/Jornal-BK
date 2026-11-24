package com.jornal.integration;

import jornal.common.events.CloudEvent;
import jornal.common.messaging.MessagingConfig;
import jornal.common.messaging.Topology;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;

/**
 * Salida de eventos hacia los subsistemas. Solo se externalizan los {@link CloudEvent}: se guardan en el
 * registro de publicaciones de Spring Modulith ({@code event_publication}, que hace de Outbox) en la misma
 * transacción del cambio, y se envían a RabbitMQ después del commit con la clave {@code <región>.<type>}.
 */
@Configuration(proxyBeanMethods = false)
@Import(MessagingConfig.class)
public class IntegrationConfig {

    @Bean
    EventExternalizationConfiguration eventExternalization() {
        return EventExternalizationConfiguration.externalizing()
                .selectByType(CloudEvent.class)
                .route(CloudEvent.class, e -> RoutingTarget.forTarget(Topology.EXCHANGE).andKey(e.routingKey()))
                .build();
    }
}
