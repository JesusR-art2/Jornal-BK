package jornal.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Pide el contacto del destinatario al core de su región ({@code GET /internal/contacts/{id}}).
 * El dato personal no se copia a este subsistema: se consulta cada vez, con timeout y reintentos.
 * Si el core no responde tras los intentos, lanza la excepción y el mensaje se reintenta más tarde.
 */
@Component
public class ContactClient {

    private static final Logger log = LoggerFactory.getLogger(ContactClient.class);

    public record Contact(UUID id, String name, String email, String phone, String locale,
                          String timezone, boolean active) {
    }

    private final RestClient http;
    private final NotificationProperties properties;

    public ContactClient(NotificationProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.http = RestClient.builder().requestFactory(factory).build();
        this.properties = properties;
    }

    /** Vacío si el usuario no existe en ese core. */
    public Optional<Contact> find(String region, UUID userId) {
        String url = properties.coreUrl().replace("{region}", region) + "/internal/contacts/" + userId;
        int attempts = Math.max(1, properties.contactAttempts());
        RestClientException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return Optional.ofNullable(http.get().uri(url)
                        .header("X-Internal-Token", properties.internalToken())
                        .retrieve()
                        .body(Contact.class));
            } catch (HttpClientErrorException.NotFound e) {
                return Optional.empty();
            } catch (RestClientException e) {
                last = e;
                log.warn("Contact {} from core {} failed (attempt {}/{}): {}", userId, region, attempt, attempts,
                        e.getMessage());
                if (attempt < attempts) {
                    pause(properties.contactBackoff().multipliedBy(attempt));
                }
            }
        }
        throw new IllegalStateException("Contact of " + userId + " not available in core " + region, last);
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
