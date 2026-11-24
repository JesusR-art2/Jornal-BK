package jornal.notifications;

import jornal.common.subsystem.SubsystemSupportConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.Clock;

/**
 * Subsistema de notificaciones: avisa a cada persona en su idioma y su zona horaria, por la app
 * (WebSocket STOMP), correo (Mailpit) y SMS y push simulados. Consume sus colas de RabbitMQ (una por región).
 */
@SpringBootApplication
@Import(SubsystemSupportConfig.class)
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationsApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationsApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
