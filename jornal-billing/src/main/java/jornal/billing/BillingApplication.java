package jornal.billing;

import jornal.common.subsystem.SubsystemSupportConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/** Subsistema billing: proceso independiente que consume sus colas de RabbitMQ (una por región). */
@SpringBootApplication
@Import(SubsystemSupportConfig.class)
public class BillingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BillingApplication.class, args);
    }
}
