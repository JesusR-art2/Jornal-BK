package jornal.common.messaging;

import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/** Topología y conversión JSON compartidas. El core y los subsistemas la importan con {@code @Import}. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MessagingProperties.class)
public class MessagingConfig {

    @Bean
    Declarables jornalTopology(MessagingProperties properties) {
        return Topology.declarables(properties.regions());
    }

    @Bean
    MessageConverter jornalMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }
}
