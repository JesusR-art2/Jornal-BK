package jornal.billing;

import jornal.common.subsystem.SubsystemSupportConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.Clock;

/**
 * Subsistema de facturación: emite el comprobante de cada país al capturarse el pago (numeración consecutiva
 * por región y tipo), guarda tasas de cambio y arma el reporte consolidado de ingresos.
 */
@SpringBootApplication
@Import(SubsystemSupportConfig.class)
@EnableConfigurationProperties(BillingProperties.class)
public class BillingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BillingApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
