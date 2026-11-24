package jornal.dispatch;

import jornal.common.subsystem.SubsystemSupportConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;

/** Subsistema dispatch: proceso independiente que consume sus colas de RabbitMQ (una por región). */
@SpringBootApplication
@ConfigurationPropertiesScan
@Import(SubsystemSupportConfig.class)
public class DispatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(DispatchApplication.class, args);
    }
}
