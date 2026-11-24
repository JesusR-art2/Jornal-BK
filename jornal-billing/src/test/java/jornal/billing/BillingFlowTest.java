package jornal.billing;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jornal.common.events.CloudEvent;
import jornal.common.events.EventTypes;
import jornal.common.messaging.Topology;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F8: cada país emite su tipo de comprobante con numeración consecutiva por región y tipo, impuestos con su tasa
 * y la fecha en la zona del lugar; el reporte consolidado muestra la tasa usada con su fecha. La prueba hace de
 * pagos (publica payment.captured) y de core (lee receipt-issued en core.<región>).
 */
@SpringBootTest
@Import(BillingFlowTest.Containers.class)
@AutoConfigureMockMvc
class BillingFlowTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer(DockerImageName.parse("postgres:16"));
        }

        @Bean @ServiceConnection
        RabbitMQContainer rabbit() {
            return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management"));
        }
    }

    private static final ParameterizedTypeReference<CloudEvent> EVENT = new ParameterizedTypeReference<>() { };

    /** Perfil mínimo por país: tipo y prefijo de comprobante, idioma, zona, moneda, escala e impuesto. */
    private record Country(String region, String type, String prefix, String locale, String zone, String currency,
                           int scale, String tax, int bps, boolean included) {
    }

    private static final List<Country> COUNTRIES = List.of(
            new Country("co", "DIAN_E_INVOICE", "FE", "es-CO", "America/Bogota", "COP", 0, "IVA", 1900, true),
            new Country("br", "NFS_E", "NFSE", "pt-BR", "America/Sao_Paulo", "BRL", 2, "ISS", 500, true),
            new Country("us", "SALES_RECEIPT", "RCPT", "en-US", "America/New_York", "USD", 2, "Sales tax (NY State)", 400, false),
            new Country("fr", "FACTURE", "FA", "fr-FR", "Europe/Paris", "EUR", 2, "TVA", 2000, true),
            new Country("jp", "QUALIFIED_INVOICE", "INV", "ja-JP", "Asia/Tokyo", "JPY", 0, "消費税", 1000, true));

    @Autowired MockMvc mvc;
    @Autowired RabbitTemplate rabbit;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;

    @Value("${jornal.security.jwt.secret}") String secret;
    @Value("${jornal.security.jwt.issuer}") String issuer;

    @Test
    void eachCountryIssuesItsReceiptTypeWithConsecutiveNumbers() throws Exception {
        for (Country c : COUNTRIES) {
            UUID client = UUID.randomUUID();
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            publish(captured(c, first, client, null));
            publish(captured(c, second, client, null));
            CloudEvent issued1 = await(c.region(), first);
            CloudEvent issued2 = await(c.region(), second);
            assertThat(issued1.data().get("receiptType")).isEqualTo(c.type());
            long n1 = Long.parseLong(issued1.data().get("receiptNumber").toString().replace(c.prefix() + "-", ""));
            long n2 = Long.parseLong(issued2.data().get("receiptNumber").toString().replace(c.prefix() + "-", ""));
            assertThat(n2).isEqualTo(n1 + 1);   // consecutivo por región y tipo
            assertThat(issued1.data().get("receiptNumber").toString()).startsWith(c.prefix() + "-0000000");
        }
    }

    @Test
    void receiptIsLocalizedWithTaxRateAndLocalTime() throws Exception {
        Country fr = COUNTRIES.get(3);
        UUID client = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        publish(captured(fr, request, client, "FR76 entreprise"));
        await("fr", request);

        String raw = mvc.perform(get("/receipts/{id}", request).header("Authorization", token(client, "BUSINESS")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode view = json.readTree(raw);
        assertThat(view.get("title").asString()).isEqualTo("Facture");
        assertThat(plain(view.get("totals").get("Total TTC").asString())).isEqualTo("1 234,56 €");
        List<String> labels = new ArrayList<>();
        view.get("totals").propertyNames().forEach(labels::add);
        assertThat(labels).anyMatch(l -> plain(l).equals("TVA (20 %, incluse)"));
        assertThat(view.get("receipt").get("timezone").asString()).isEqualTo("Europe/Paris");
        assertThat(view.get("receipt").get("customerTaxIdNumber").asString()).isEqualTo("FR76 entreprise");

        String html = mvc.perform(get("/receipts/{id}/html", request).header("Authorization", token(client, "BUSINESS")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("<html lang=\"fr-FR\">").contains("Facture").contains("Déplacement")
                .contains("Jornal France SAS");

        // El comprobante en otro idioma y el control de acceso
        String en = mvc.perform(get("/receipts/{id}", request).param("lang", "en-US")
                        .header("Authorization", token(client, "BUSINESS")))
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(en).get("totals").get("Total").asString()).isEqualTo("€1,234.56");
        mvc.perform(get("/receipts/{id}", request).header("Authorization", token(UUID.randomUUID(), "APPLICANT")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/receipts/{id}", request).header("Authorization", token(UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void duplicateCaptureIssuesASingleReceipt() throws Exception {
        Country co = COUNTRIES.get(0);
        UUID request = UUID.randomUUID();
        CloudEvent event = captured(co, request, UUID.randomUUID(), null);
        publish(event);
        await("co", request);
        // Otro mensaje distinto (reintento de pagos) para el mismo pedido
        publish(captured(co, request, UUID.randomUUID(), null));
        Thread.sleep(1500);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM receipts WHERE request_id = ?", Integer.class, request))
                .isEqualTo(1);
    }

    @Test
    void consolidatedReportShowsTheRateUsedWithItsDate() throws Exception {
        String admin = token(UUID.randomUUID(), "ADMIN");
        for (Country c : COUNTRIES) {
            UUID request = UUID.randomUUID();
            publish(captured(c, request, UUID.randomUUID(), null));
            await(c.region(), request);
        }
        mvc.perform(post("/admin/rates/refresh").header("Authorization", admin)).andExpect(status().isOk());
        // Carga manual: la tasa oficial del día reemplaza a la simulada para EUR/USD
        mvc.perform(post("/admin/rates").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"base\":\"EUR\",\"quote\":\"USD\",\"rate\":1.1,\"source\":\"BCE (carga manual)\"}"))
                .andExpect(status().isCreated());

        String raw = mvc.perform(get("/admin/reports/revenue").param("currency", "USD").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode report = json.readTree(raw);
        assertThat(report.get("missingRates").size()).isZero();
        Map<String, JsonNode> byRegion = new HashMap<>();
        report.get("regions").forEach(r -> byRegion.put(r.get("region").asString(), r));
        assertThat(byRegion).containsKeys("co", "br", "us", "fr", "jp");
        assertThat(byRegion.get("us").get("rate").get("method").asString()).isEqualTo("SAME");
        JsonNode cop = byRegion.get("co").get("rate");
        assertThat(cop.get("pair").asString()).isEqualTo("COP/USD");
        assertThat(cop.get("source").asString()).contains("simulado");
        assertThat(cop.get("asOf").asString()).isNotBlank();
        assertThat(byRegion.get("fr").get("rate").get("rate").asString()).isEqualTo("1.1");

        // En euros: COP pasa por USD (tasa cruzada)
        String eur = mvc.perform(get("/admin/reports/revenue").param("currency", "EUR").header("Authorization", admin))
                .andReturn().getResponse().getContentAsString();
        json.readTree(eur).get("regions").forEach(r -> {
            if (r.get("region").asString().equals("co")) {
                assertThat(r.get("rate").get("method").asString()).isEqualTo("CROSS_USD");
            }
        });

        // Solo administradores
        mvc.perform(get("/admin/reports/revenue").header("Authorization", token(UUID.randomUUID(), "APPLICANT")))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ ayudas

    private CloudEvent captured(Country c, UUID request, UUID client, String taxId) {
        long subtotal = c.scale() == 0 ? 100_000 : 100_000;      // 1000,00 o 100000
        long travel = 10_000;
        long tax = c.included() ? 18_456 : 4_400;
        long tip = 0;
        long total = subtotal + travel + (c.included() ? 0 : tax) + tip + 13_456;
        Map<String, Object> d = new HashMap<>();
        d.put("requestId", request.toString());
        d.put("clientId", client.toString());
        d.put("workerId", UUID.randomUUID().toString());
        d.put("currency", c.currency());
        d.put("moneyScale", c.scale());
        d.put("subtotalMinor", subtotal);
        d.put("travelFeeMinor", travel);
        d.put("taxMinor", tax);
        d.put("taxIncluded", c.included());
        d.put("tipMinor", tip);
        d.put("totalMinor", c.region().equals("fr") ? 123_456 : total);
        d.put("platformFeeMinor", 20_000);
        d.put("timezone", c.zone());
        d.put("receiptType", c.type());
        d.put("receiptPrefix", c.prefix());
        d.put("regionLocale", c.locale());
        d.put("taxRates", Map.of(c.tax(), c.bps()));
        d.put("categoryName", "Bodega");
        d.put("addressText", "Dirección de prueba");
        d.put("customerName", "Ana Prueba");
        if (taxId != null) {
            d.put("customerLegalName", "Entreprise Fictive SAS");
            d.put("customerTaxIdType", "SIRET");
            d.put("customerTaxIdNumber", taxId);
        }
        d.put("lines", List.of(
                Map.of("kind", "SERVICE", "description", "Bodega", "quantity", 2, "unitAmountMinor", subtotal / 2,
                        "amountMinor", subtotal, "includedInTotal", true),
                Map.of("kind", "TRAVEL_FEE", "description", "travel_fee", "quantity", 1, "unitAmountMinor", travel,
                        "amountMinor", travel, "includedInTotal", true),
                Map.of("kind", "TAX", "description", c.tax(), "quantity", 1, "unitAmountMinor", tax,
                        "amountMinor", tax, "includedInTotal", !c.included())));
        return CloudEvent.of("jornal-payments", EventTypes.PAYMENT_CAPTURED, c.region(), request.toString(), 1, d,
                Instant.now(), request.toString());
    }

    /** CLDR usa espacios finos no separables en francés (U+202F y U+00A0). */
    private static String plain(String s) {
        return s.replace(' ', ' ').replace(' ', ' ');
    }

    private void publish(CloudEvent event) {
        rabbit.convertAndSend(Topology.EXCHANGE, event.routingKey(), event);
    }

    private final Map<String, CloudEvent> received = new HashMap<>();

    private CloudEvent await(String region, UUID request) {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (received.containsKey(request.toString())) return received.get(request.toString());
            CloudEvent e = rabbit.receiveAndConvert(Topology.queue(Topology.CORE, region), 300, EVENT);
            if (e != null && EventTypes.BILLING_RECEIPT_ISSUED.equals(e.type())) received.put(e.subject(), e);
        }
        throw new AssertionError("No receipt-issued for " + request);
    }

    private String token(UUID user, String role) {
        Instant now = Instant.now();
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).issuedAt(now)
                .expiresAt(now.plusSeconds(600)).subject(user.toString())
                .claim("role", role).claim("region", "FR").build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
