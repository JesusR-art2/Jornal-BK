package jornal.common.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** {@code jornal.messaging.regions}: regiones con colas propias (variable MESSAGING_REGIONS). */
@ConfigurationProperties("jornal.messaging")
public record MessagingProperties(List<String> regions) {

    public MessagingProperties {
        if (regions == null || regions.isEmpty()) {
            throw new IllegalStateException("jornal.messaging.regions must list at least one region");
        }
        regions = List.copyOf(regions);
    }
}
