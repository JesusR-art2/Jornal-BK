package jornal.payments;

import jornal.common.subsystem.SubsystemSupportConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.Clock;

/** Subsistema payments: proceso independiente que consume sus colas de RabbitMQ (una por región). */
@SpringBootApplication
@ConfigurationPropertiesScan
@Import(SubsystemSupportConfig.class)
public class PaymentsApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentsApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
