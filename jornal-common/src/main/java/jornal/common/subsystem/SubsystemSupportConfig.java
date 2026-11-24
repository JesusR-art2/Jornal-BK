package jornal.common.subsystem;

import jornal.common.messaging.MessagingConfig;
import jornal.common.messaging.MessagingProperties;
import jornal.common.messaging.Topology;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Base de cada subsistema: topología, inbox, caché de perfiles regionales y listener de sus colas.
 * Cada subsistema la importa con {@code @Import(SubsystemSupportConfig.class)} y agrega sus propios
 * {@link CloudEventHandler}.
 */
@Configuration(proxyBeanMethods = false)
@Import(MessagingConfig.class)
@EnableConfigurationProperties(SubsystemProperties.class)
@ComponentScan(basePackageClasses = SubsystemSupportConfig.class)
@EnableScheduling
public class SubsystemSupportConfig {

    @Bean
    String[] subsystemQueueNames(SubsystemProperties subsystem, MessagingProperties messaging) {
        if (subsystem.name() == null || !Topology.SUBSYSTEMS.contains(subsystem.name())) {
            throw new IllegalStateException("jornal.subsystem.name must be one of " + Topology.SUBSYSTEMS);
        }
        return Topology.queuesOf(subsystem.name(), messaging.regions()).toArray(String[]::new);
    }
}
