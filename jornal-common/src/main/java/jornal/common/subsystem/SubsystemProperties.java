package jornal.common.subsystem;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code jornal.subsystem.name}: payments, dispatch, notifications o billing. Define qué colas escucha. */
@ConfigurationProperties("jornal.subsystem")
public record SubsystemProperties(String name) {
}
