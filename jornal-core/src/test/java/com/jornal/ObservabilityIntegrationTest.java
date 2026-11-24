package com.jornal;

import com.jornal.requests.RequestStats;
import com.sun.net.httpserver.HttpServer;
import jornal.common.events.CloudEvent;
import jornal.common.events.EventTypes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * F10: el id de correlación del gateway llega a la respuesta y al mensaje; métricas de pedidos por estado y
 * región para Prometheus; la consola del administrador muestra el tablero y señala qué servicio está caído.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "jornal.region.code=CO",
        "jornal.admin.email=admin.co@example.test",
        "jornal.admin.password=AdminPass123"})
@AutoConfigureMockMvc
class ObservabilityIntegrationTest {

    private static final ParameterizedTypeReference<CloudEvent> EVENT = new ParameterizedTypeReference<>() { };
    private static final HttpServer HEALTHY = healthyService();

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        registry.add("jornal.console.services", () -> "payments=http://127.0.0.1:" + HEALTHY.getAddress().getPort()
                + "/actuator/health,billing=http://127.0.0.1:1/actuator/health");
    }

    @AfterAll
    static void stop() {
        HEALTHY.stop(0);
    }

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired RabbitTemplate rabbit;
    @Autowired RequestStats stats;
    @Autowired io.micrometer.core.instrument.MeterRegistry registry;

    @Test
    void correlationIdTravelsFromTheRequestToTheMessage() throws Exception {
        String client = token("APPLICANT");
        String cid = "obs-" + UUID.randomUUID();
        String raw = mvc.perform(post("/requests").header("Authorization", client)
                        .header("Idempotency-Key", "obs-" + UUID.randomUUID()).header("X-Correlation-Id", cid)
                        .contentType(MediaType.APPLICATION_JSON).content(draft()))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-Id", cid))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(raw).get("id").asString();

        CloudEvent created = await("payments.co", EventTypes.REQUEST_CREATED, id);
        assertThat(created.correlationid()).isEqualTo(cid);

        // Sin header, el filtro genera uno y lo devuelve
        mvc.perform(get("/region-profile")).andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    void prometheusExposesRequestsByStatusAndRegion() throws Exception {
        // (Spring Boot no exporta a Prometheus en pruebas; se revisa el registro de métricas que Prometheus lee)
        stats.refresh();
        assertThat(registry.find("jornal.requests").tag("region", "co").tag("status", "COMPLETED").gauge()).isNotNull();
        assertThat(registry.find("jornal.requests").tag("region", "co").gauges()).hasSize(11);
        mvc.perform(get("/actuator/prometheus")).andExpect(status().is(org.hamcrest.Matchers.not(401)));
    }

    @Test
    void consoleShowsTheDashboardAndWhichServiceIsDown() throws Exception {
        String admin = "Bearer " + login("admin.co@example.test", "AdminPass123");
        String raw = mvc.perform(get("/admin/console").header("Authorization", admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode console = json.readTree(raw);
        assertThat(console.get("dashboard").get("region").asString()).isEqualTo("CO");
        assertThat(console.get("dashboard").get("requestsByStatus").has("PENDING_PAYMENT")).isTrue();
        assertThat(console.get("dashboard").has("workersAvailable")).isTrue();
        assertThat(console.get("down").toString()).contains("billing").doesNotContain("payments");
        assertThat(console.get("services").get(0).get("name").asString()).isEqualTo("billing");   // caídos primero
        assertThat(console.get("services").get(1).get("status").asString()).isEqualTo("UP");
        assertThat(console.get("queues").has("available")).isTrue();

        mvc.perform(get("/admin/console").header("Authorization", token("APPLICANT"))).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ utilidades

    private static HttpServer healthyService() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/actuator/health", exchange -> {
                byte[] body = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private CloudEvent await(String queue, String type, String requestId) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            CloudEvent e = rabbit.receiveAndConvert(queue, 300, EVENT);
            if (e != null && type.equals(e.type()) && requestId.equals(e.subject())) return e;
        }
        throw new AssertionError("No " + type + " for " + requestId);
    }

    private String draft() throws Exception {
        String raw = mvc.perform(get("/categories").param("q", "warehouse").header("Authorization", token("APPLICANT")))
                .andReturn().getResponse().getContentAsString();
        String categoryId = json.readTree(raw).get(0).get("id").asString();
        String day = LocalDate.now(ZoneId.of("America/Bogota")).plusDays(2).toString();
        return """
                {"categoryId":"%s","units":2,"scheduledLocal":"%sT08:00","timezone":"America/Bogota",
                 "address":{"street_address":"Calle 26 # 68-35","city":"Bogotá","department":"Cundinamarca"},
                 "latitude":4.6584,"longitude":-74.0937,"paymentMethod":"CARD","tipMinor":5000}
                """.formatted(categoryId, day);
    }

    private String token(String role) throws Exception {
        String email = "obs-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"Clave12345","givenName":"Ana","familyName":"Prueba",
                         "rol":"%s","acceptedTerms":true}
                        """.formatted(email, role)))
                .andExpect(status().isCreated());
        return "Bearer " + login(email, "Clave12345");
    }

    private String login(String email, String password) throws Exception {
        String raw = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(raw).get("accessToken").asString();
    }
}
