package jornal.dispatch;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param offerTimeout  plazo del trabajador para aceptar una oferta
 * @param searchTimeout sin candidatos durante este tiempo, la búsqueda falla
 * @param maxOffers     ofertas por pedido antes de rendirse
 * @param liveTtl       vigencia de una posición en vivo
 */
@ConfigurationProperties("jornal.dispatch")
public record DispatchProperties(Duration offerTimeout, Duration searchTimeout, int maxOffers, Duration liveTtl) {

    public DispatchProperties {
        if (offerTimeout == null) offerTimeout = Duration.ofSeconds(45);
        if (searchTimeout == null) searchTimeout = Duration.ofSeconds(120);
        if (maxOffers <= 0) maxOffers = 5;
        if (liveTtl == null) liveTtl = Duration.ofSeconds(60);
    }
}
