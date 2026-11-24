package jornal.billing;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/** Emisor (la plataforma) de cada región; datos ficticios de demostración. */
@ConfigurationProperties("jornal.billing")
public record BillingProperties(Map<String, Issuer> issuers) {

    public record Issuer(String name, String taxId) {
    }

    public Issuer issuerOf(String region) {
        Issuer issuer = issuers == null ? null : issuers.get(region);
        return issuer != null ? issuer : new Issuer("Jornal (" + region.toUpperCase() + ")", "N/A (VERIFICAR)");
    }
}
