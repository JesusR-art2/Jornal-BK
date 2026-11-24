package com.jornal;

import com.jornal.requests.services.RequestTimeouts;
import jornal.common.events.CloudEvent;
import jornal.common.events.EventTypes;
import jornal.common.messaging.Topology;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F4: la saga del pedido con los subsistemas simulados. La prueba hace de pagos, asignación y facturación:
 * lee los comandos que el core deja en sus colas y responde publicando en {@code jornal.events}.
 * Mismas propiedades que {@link LocalizationIntegrationTest} para reutilizar el contexto.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "jornal.region.code=CO",
        "jornal.admin.email=admin.co@example.test",
        "jornal.admin.password=AdminPass123"})
@AutoConfigureMockMvc
class RequestSagaIntegrationTest {

    private static final ParameterizedTypeReference<CloudEvent> EVENT = new ParameterizedTypeReference<>() { };

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired RabbitTemplate rabbit;
    @Autowired RequestTimeouts timeouts;

    @Test
    void happyPathFromCartToCompleted() throws Exception {
        String client = token("APPLICANT");
        Worker worker = worker();

        // Cotizar (no guarda nada) y crear con Idempotency-Key
        mvc.perform(post("/requests/quote").header("Authorization", client)
                        .contentType(MediaType.APPLICATION_JSON).content(draft()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMinor").value((int) (170000 + travelFee() + 5000)))   // 2 × 85000 + desplazamiento + propina
                .andExpect(jsonPath("$.taxMinor").value(28420))
                .andExpect(jsonPath("$.taxIncluded").value(true));

        String key = UUID.randomUUID().toString();
        String id = create(client, key);
        mvc.perform(post("/requests").header("Authorization", client).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(draft()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));                  // misma clave: mismo pedido

        // Pagos recibe el pedido y autoriza
        CloudEvent created = await("payments.co", EventTypes.REQUEST_CREATED, id);
        assertThat(created.data()).containsEntry("totalMinor", (int) (170000 + travelFee() + 5000))
                .containsEntry("currency", "COP");
        reply(EventTypes.PAYMENT_AUTHORIZED, id, Map.of("attempt", 1));
        awaitStatus(client, id, "SEARCHING_WORKER");

        // Asignación busca trabajador y lo asigna
        CloudEvent search = await("dispatch.co", EventTypes.REQUEST_ASSIGNMENT_REQUESTED, id);
        assertThat(search.data()).containsEntry("categorySlug", "warehouse");
        reply(EventTypes.DISPATCH_WORKER_ASSIGNED, id, Map.of("workerId", worker.id()));
        awaitStatus(client, id, "ASSIGNED");

        // El trabajador avanza; otro usuario no puede
        mvc.perform(post("/requests/{id}/en-route", id).header("Authorization", token("WORKER")))
                .andExpect(status().isForbidden());
        for (String step : new String[]{"en-route", "start", "finish"}) {
            mvc.perform(post("/requests/{id}/" + step, id).header("Authorization", worker.token()))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/requests/{id}", id).header("Authorization", worker.token()))
                .andExpect(jsonPath("$.status").value("PENDING_CONFIRMATION"));

        // El cliente confirma: se pide la captura; pagos captura y facturación emite el comprobante
        mvc.perform(post("/requests/{id}/confirm", id).header("Authorization", client)).andExpect(status().isOk());
        await("payments.co", EventTypes.REQUEST_CAPTURE_REQUESTED, id);
        reply(EventTypes.PAYMENT_CAPTURED, id, Map.of("attempt", 1));
        reply(EventTypes.BILLING_RECEIPT_ISSUED, id, Map.of("receiptNumber", "FE-000001"));
        awaitStatus(client, id, "COMPLETED");

        mvc.perform(get("/requests/{id}/history", id).header("Authorization", client))
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].to").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$[6].to").value("COMPLETED"))
                .andExpect(jsonPath("$[6].actor").value("billing"));
    }

    @Test
    void paymentFailureCancelsTheRequest() throws Exception {
        String client = token("APPLICANT");
        String id = create(client, UUID.randomUUID().toString());
        reply(EventTypes.PAYMENT_FAILED, id, Map.of("attempt", 1, "reason", "DECLINED"));
        awaitStatus(client, id, "CANCELLED");
        mvc.perform(get("/requests/{id}", id).header("Authorization", client))
                .andExpect(jsonPath("$.cancelReason").value("PAYMENT_FAILED:DECLINED"));
    }

    @Test
    void noWorkerCancelsWithPaymentCompensation() throws Exception {
        String client = token("BUSINESS_LIKE_APPLICANT");
        String id = create(client, UUID.randomUUID().toString());
        reply(EventTypes.PAYMENT_AUTHORIZED, id, Map.of("attempt", 1));
        awaitStatus(client, id, "SEARCHING_WORKER");

        reply(EventTypes.DISPATCH_ASSIGNMENT_FAILED, id, Map.of("reason", "NOBODY_ACCEPTED"));
        awaitStatus(client, id, "CANCELLED");

        // Compensación: pagos recibe la cancelación y sabe que había un pago autorizado
        CloudEvent cancelled = await("payments.co", EventTypes.REQUEST_CANCELLED, id);
        assertThat(cancelled.data()).containsEntry("paymentAuthorized", true)
                .containsEntry("reason", "NO_WORKER_AVAILABLE");
    }

    @Test
    void repricingInvalidatesTheOldPaymentAttempt() throws Exception {
        String client = token("APPLICANT");
        String id = create(client, UUID.randomUUID().toString());

        mvc.perform(patch("/requests/{id}", id).header("Authorization", client)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"units\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentAttempt").value(2))
                .andExpect(jsonPath("$.totalMinor").value((int) (255000 + travelFee() + 5000)));  // 3 × 85000 + desplazamiento + propina
        await("payments.co", EventTypes.REQUEST_REPRICED, id);

        reply(EventTypes.PAYMENT_AUTHORIZED, id, Map.of("attempt", 1));   // respuesta vieja: se ignora
        reply(EventTypes.PAYMENT_AUTHORIZED, id, Map.of("attempt", 2));
        awaitStatus(client, id, "SEARCHING_WORKER");
    }

    @Test
    void clientCancelsAndTimeoutsCloseStaleRequests() throws Exception {
        String client = token("APPLICANT");
        String cancelled = create(client, UUID.randomUUID().toString());
        mvc.perform(post("/requests/{id}/cancel", cancelled).header("Authorization", client)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Ya no lo necesito\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/requests/{id}/cancel", cancelled).header("Authorization", client))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQ006_INVALID_TRANSITION"));

        // Sin respuesta de pagos: a los 2 minutos se cancela
        String stale = create(client, UUID.randomUUID().toString());
        assertThat(timeouts.expirePendingPayments(Instant.now().plus(3, ChronoUnit.MINUTES))).isPositive();
        mvc.perform(get("/requests/{id}", stale).header("Authorization", client))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("PAYMENT_TIMEOUT"));
    }

    @Test
    void validationUsesTheRegionProfile() throws Exception {
        String client = token("APPLICANT");
        mvc.perform(post("/requests").header("Authorization", client).header("Idempotency-Key", "k-" + UUID.randomUUID())
                        .header("Accept-Language", "es-CO")
                        .contentType(MediaType.APPLICATION_JSON).content(draft().replace("\"CARD\"", "\"PAYPAY\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQ004_PAYMENT_METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.detail").value("El medio de pago PAYPAY no está disponible en este país"));
        mvc.perform(post("/requests").header("Authorization", client)
                        .contentType(MediaType.APPLICATION_JSON).content(draft()))
                .andExpect(jsonPath("$.code").value("REQ008_IDEMPOTENCY_KEY_REQUIRED"));
        mvc.perform(post("/requests/quote").header("Authorization", client)
                        .contentType(MediaType.APPLICATION_JSON).content(draft().replace("America/Bogota", "Asia/Tokyo")))
                .andExpect(jsonPath("$.code").value("REQ009_TIMEZONE_NOT_ALLOWED"));
        mvc.perform(post("/requests/quote").header("Authorization", token("WORKER"))
                        .contentType(MediaType.APPLICATION_JSON).content(draft()))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ utilidades

    /** Tarifa actual: otras pruebas del mismo contexto modifican el perfil. */
    private long travelFee() throws Exception {
        String raw = mvc.perform(get("/region-profile")).andReturn().getResponse().getContentAsString();
        return json.readTree(raw).get("travelFeeMinor").asLong();
    }

    private record Worker(String id, String token) {
    }

    private String draft() throws Exception {
        String categoryId = categoryId("warehouse");
        String day = LocalDate.now(ZoneId.of("America/Bogota")).plusDays(2).toString();
        return """
                {"categoryId":"%s","units":2,"scheduledLocal":"%sT08:00","timezone":"America/Bogota",
                 "address":{"street_address":"Calle 26 # 68-35","city":"Bogotá","department":"Cundinamarca"},
                 "latitude":4.6584,"longitude":-74.0937,"paymentMethod":"CARD","tipMinor":5000,
                 "notes":"Bodega del primer piso"}
                """.formatted(categoryId, day);
    }

    private String categoryId(String slug) throws Exception {
        String raw = mvc.perform(get("/categories").param("q", slug).header("Authorization", token("APPLICANT")))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(raw).get(0).get("id").asString();
    }

    private String create(String token, String key) throws Exception {
        String raw = mvc.perform(post("/requests").header("Authorization", token).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(draft()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(raw).get("id").asString();
    }

    private void reply(String type, String requestId, Map<String, Object> data) {
        Map<String, Object> payload = new HashMap<>(data);
        payload.put("requestId", requestId);
        CloudEvent event = CloudEvent.of("test-subsystem", type, "co", requestId, 1, payload, Instant.now(), requestId);
        rabbit.convertAndSend(Topology.EXCHANGE, event.routingKey(), event);
    }

    private CloudEvent await(String queue, String type, String requestId) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            CloudEvent e = rabbit.receiveAndConvert(queue, 500, EVENT);
            if (e != null && type.equals(e.type()) && requestId.equals(e.subject())) return e;
        }
        throw new AssertionError("No " + type + " for " + requestId + " in " + queue);
    }

    private void awaitStatus(String token, String id, String expected) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        String current = null;
        while (System.currentTimeMillis() < deadline) {
            String raw = mvc.perform(get("/requests/{id}", id).header("Authorization", token))
                    .andReturn().getResponse().getContentAsString();
            current = json.readTree(raw).get("status").asString();
            if (expected.equals(current)) return;
            Thread.sleep(200);
        }
        throw new AssertionError("Expected " + expected + " but was " + current);
    }

    private Worker worker() throws Exception {
        String token = token("WORKER");
        String raw = mvc.perform(get("/users/me").header("Authorization", token))
                .andReturn().getResponse().getContentAsString();
        return new Worker(json.readTree(raw).get("id").asString(), token);
    }

    private String token(String role) throws Exception {
        String rol = role.startsWith("BUSINESS") ? "APPLICANT" : role;
        String email = "saga-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"Clave12345","givenName":"Ana","familyName":"Prueba",
                         "rol":"%s","acceptedTerms":true}
                        """.formatted(email, rol)))
                .andExpect(status().isCreated());
        String raw = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Clave12345\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(raw);
        return "Bearer " + node.get("accessToken").asString();
    }
}
