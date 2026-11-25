package jornal.common.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * {@code jornal.messaging.regions}: regiones con colas propias (variable MESSAGING_REGIONS).
 * {@code jornal.messaging.consumers}: procesos cuyas colas se declaran (MESSAGING_CONSUMERS); vacío = todos. En un
 * despliegue parcial (por ejemplo sin asignación ni facturación) evita colas que nadie consume.
 */
@ConfigurationProperties("jornal.messaging")
public record MessagingProperties(List<String> regions, List<String> consumers) {

    public MessagingProperties {
        if (regions == null || regions.isEmpty()) {
            throw new IllegalStateException("jornal.messaging.regions must list at least one region");
        }
        regions = List.copyOf(regions);
        consumers = consumers == null ? List.of() : consumers.stream().filter(c -> !c.isBlank()).toList();
    }
}
