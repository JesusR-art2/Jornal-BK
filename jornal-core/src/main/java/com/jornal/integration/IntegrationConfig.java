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
import org.springframework.amqp.core.Declarables;

import java.util.List;
import java.util.Locale;

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

    /**
     * Topología de la región propia (su cola y las de los subsistemas para esa región), aunque la región no esté en
     * {@code MESSAGING_REGIONS}. Así un país nuevo no pierde mensajes: sus colas existen desde que arranca su core,
     * y los subsistemas las consumen cuando se les agrega la región. Declarar dos veces es inofensivo.
     */
    @Bean
    Declarables ownRegionTopology(@Value("${jornal.region.code}") String region,
                                  jornal.common.messaging.MessagingProperties messaging) {
        return Topology.declarables(List.of(region.toLowerCase(Locale.ROOT)), messaging.consumers());
    }
}
