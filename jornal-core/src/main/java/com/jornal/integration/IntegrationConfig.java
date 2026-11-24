package com.jornal.integration;

import jornal.common.events.CloudEvent;
import jornal.common.messaging.MessagingConfig;
import jornal.common.messaging.Topology;
import jornal.common.subsystem.Inbox;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;

/**
 * Mensajería del core.
 * <ul>
 *   <li>Salida: solo se externalizan los {@link CloudEvent}. Se guardan en {@code event_publication} (Outbox)
 *       en la misma transacción del cambio y se envían a RabbitMQ después del commit con la clave
 *       {@code <región>.<type>}.</li>
 *   <li>Entrada: la cola {@code core.<región>} trae las respuestas de los subsistemas; el {@link Inbox}
 *       descarta duplicados.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@Import({MessagingConfig.class, Inbox.class})
public class IntegrationConfig {

    @Bean
    EventExternalizationConfiguration eventExternalization() {
        return EventExternalizationConfiguration.externalizing()
                .selectByType(CloudEvent.class)
                .route(CloudEvent.class, e -> RoutingTarget.forTarget(Topology.EXCHANGE).andKey(e.routingKey()))
                .build();
    }

    @Bean
    String coreQueueName(@Value("${jornal.region.code}") String region) {
        return Topology.queue(Topology.CORE, region);
    }
}
