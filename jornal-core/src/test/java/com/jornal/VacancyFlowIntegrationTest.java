package com.jornal;

import jornal.common.events.CloudEvent;
import jornal.common.events.EventTypes;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo de vacantes: publicar con aviso a los cercanos, postularse, elegir a varios, confirmar asistencia,
 * y tras la hora de fin finalizar (pasa a pago) o cancelar con justificación.
 * Mismas propiedades que {@link RequestSagaIntegrationTest} para reutilizar el contexto.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "jornal.region.code=CO",
        "jornal.admin.email=admin.co@example.test",
        "jornal.admin.password=AdminPass123"})
@AutoConfigureMockMvc
class VacancyFlowIntegrationTest {

    private static final ParameterizedTypeReference<CloudEvent> EVENT = new ParameterizedTypeReference<>() { };

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired RabbitTemplate rabbit;
    @Autowired JdbcTemplate jdbc;

    private record User(String id, String token) {
    }

    @Test
    void fromPublicationToPayout() throws Exception {
        User employer = user("APPLICANT");
        User near1 = worker(4.6600, -74.0900);      // a unos cientos de metros
        User near2 = worker(4.6700, -74.1000);
        User far = worker(6.2442, -75.5812);        // Medellín: fuera de su radio de 10 km

        // 1. Publicar: solo los cercanos reciben el aviso emergente
        String vacancyId = id(mvc.perform(post("/vacancies").header("Authorization", employer.token())
                        .contentType(MediaType.APPLICATION_JSON).content(draft()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.items[0]").value("Botas de seguridad"))
                .andExpect(jsonPath("$.currency").value("COP"))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(post("/vacancies").header("Authorization", near1.token())
                        .contentType(MediaType.APPLICATION_JSON).content(draft()))
                .andExpect(status().isForbidden());

        CloudEvent published = await(EventTypes.VACANCY_PUBLISHED, vacancyId, e -> true);
        String nearby = published.data().get("nearbyWorkers").toString();
        assertThat(nearby).contains(near1.id(), near2.id()).doesNotContain(far.id());

        // 2. El trabajador ve el listado con la distancia y se postula
        mvc.perform(get("/vacancies/{id}", vacancyId).header("Authorization", near1.token()))
                .andExpect(jsonPath("$.description").value("Cargar y descargar el camión"))
                .andExpect(jsonPath("$.distanceKm").isNumber())
                .andExpect(jsonPath("$.myApplication").doesNotExist());
        String list = mvc.perform(get("/vacancies").param("size", "100").header("Authorization", near1.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(list).contains(vacancyId);

        String app1 = apply(near1, vacancyId);
        String app2 = apply(near2, vacancyId);
        String app3 = apply(far, vacancyId);
        mvc.perform(post("/vacancies/{id}/apply", vacancyId).header("Authorization", near1.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VAC006_ALREADY_APPLIED"));
        mvc.perform(post("/vacancies/{id}/apply", vacancyId).header("Authorization", employer.token()))
                .andExpect(status().isForbidden());

        // 3. El empleador ve los candidatos ordenados (el lejano al final) y elige a varios
        mvc.perform(get("/vacancies/{id}/applications", vacancyId).header("Authorization", employer.token()))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[2].workerId").value(far.id()));
        mvc.perform(get("/vacancies/{id}/applications", vacancyId).header("Authorization", near1.token()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/vacancies/{id}/selections", vacancyId).header("Authorization", employer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicationIds\":[\"%s\",\"%s\",\"%s\"]}".formatted(app1, app2, app3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VAC009_NO_SLOTS"));
        mvc.perform(post("/vacancies/{id}/selections", vacancyId).header("Authorization", employer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicationIds\":[\"%s\",\"%s\"]}".formatted(app1, app2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("SELECTED"));
        vacancyStatus(employer, vacancyId, "FILLED");

        // 4. Aviso al elegido (con día, hora, lugar e implementos) y botón de asistencia
        CloudEvent selected = await(EventTypes.VACANCY_APPLICATION_CHANGED, vacancyId,
                e -> "SELECTED".equals(e.data().get("status")) && near1.id().equals(e.data().get("workerId")));
        assertThat(selected.data()).containsKeys("startsAt", "endsAt", "addressText")
                .containsEntry("items", "Botas de seguridad, Guantes");

        // near2 no puede ir: el cupo se libera y el empleador elige a otro
        attendance(near2, vacancyId, false, "DECLINED");
        vacancyStatus(employer, vacancyId, "OPEN");
        mvc.perform(post("/vacancies/{id}/selections", vacancyId).header("Authorization", employer.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"applicationIds\":[\"%s\"]}".formatted(app3)))
                .andExpect(status().isOk());
        attendance(near1, vacancyId, true, "CONFIRMED");
        attendance(far, vacancyId, true, "CONFIRMED");
        vacancyStatus(employer, vacancyId, "FILLED");

        // 5. Antes de la hora de fin no se puede cerrar a nadie
        mvc.perform(post("/vacancies/{id}/applications/{a}/complete", vacancyId, app1)
                        .header("Authorization", employer.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VAC010_NOT_ENDED"));

        // El trabajo ya pasó: aparece en el listado de vacantes por cerrar
        jdbc.update("UPDATE vacancies SET starts_at = now() - interval '9 hours', ends_at = now() - interval '1 hour' WHERE id = ?",
                UUID.fromString(vacancyId));
        assertThat(mvc.perform(get("/vacancies/mine").param("pendingClosure", "true")
                        .header("Authorization", employer.token()))
                .andReturn().getResponse().getContentAsString()).contains(vacancyId);
        mvc.perform(get("/vacancies/{id}/applications", vacancyId).param("status", "CONFIRMED")
                        .header("Authorization", employer.token()))
                .andExpect(jsonPath("$.length()").value(2));

        // 6. Finalizar a uno (pasa al formato de pago) y cancelar al otro con justificación
        mvc.perform(post("/vacancies/{id}/applications/{a}/complete", vacancyId, app1)
                        .header("Authorization", employer.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.payoutMinor").value(120000))
                .andExpect(jsonPath("$.payoutStatus").value("PENDING"));
        mvc.perform(post("/vacancies/{id}/applications/{a}/cancel", vacancyId, app3)
                        .header("Authorization", employer.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"NO_SHOW\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/vacancies/{id}/applications/{a}/cancel", vacancyId, app3)
                        .header("Authorization", employer.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"NO_SHOW\",\"justification\":\"No se presentó al turno\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.payoutMinor").doesNotExist());
        vacancyStatus(employer, vacancyId, "CLOSED");

        // 7. Formato de pago: solo quien terminó; luego se marca pagado
        String payouts = mvc.perform(get("/vacancies/payouts").param("status", "PENDING")
                        .header("Authorization", employer.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(payouts).contains(app1).doesNotContain(app3);
        // Pago a mano (configuración por defecto): pagar menos exige justificación
        mvc.perform(post("/vacancies/{id}/applications/{a}/paid", vacancyId, app1)
                        .header("Authorization", employer.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":100000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VAC014_JUSTIFICATION_REQUIRED"));
        mvc.perform(post("/vacancies/{id}/applications/{a}/paid", vacancyId, app1)
                        .header("Authorization", employer.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":100000,\"justification\":\"Llegó una hora tarde\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payoutStatus").value("PAID"))
                .andExpect(jsonPath("$.payoutMode").value("MANUAL"))
                .andExpect(jsonPath("$.paidAmountMinor").value(100000))
                .andExpect(jsonPath("$.paymentNote").value("Llegó una hora tarde"));
        mvc.perform(get("/vacancies/applications/mine").header("Authorization", near1.token()))
                .andExpect(jsonPath("$.items[0].payoutStatus").value("PAID"));
    }

    @Test
    void automaticPayoutRunsOnlyWhenFinishing() throws Exception {
        User employer = user("APPLICANT");
        mvc.perform(get("/vacancies/settings").header("Authorization", employer.token()))
                .andExpect(jsonPath("$.autoPayout").value(false));
        mvc.perform(put("/vacancies/settings").header("Authorization", employer.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"autoPayout\":true}"))
                .andExpect(jsonPath("$.autoPayout").value(true));

        User ok = worker(4.6600, -74.0900);
        User absent = worker(4.6610, -74.0910);
        String vacancyId = id(mvc.perform(post("/vacancies").header("Authorization", employer.token())
                        .contentType(MediaType.APPLICATION_JSON).content(draft()))
                .andReturn().getResponse().getContentAsString());
        String appOk = apply(ok, vacancyId);
        String appAbsent = apply(absent, vacancyId);
        mvc.perform(post("/vacancies/{id}/selections", vacancyId).header("Authorization", employer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicationIds\":[\"%s\",\"%s\"]}".formatted(appOk, appAbsent)))
                .andExpect(status().isOk());
        attendance(ok, vacancyId, true, "CONFIRMED");
        attendance(absent, vacancyId, true, "CONFIRMED");
        jdbc.update("UPDATE vacancies SET starts_at = now() - interval '9 hours', ends_at = now() - interval '1 hour' WHERE id = ?",
                UUID.fromString(vacancyId));

        // Cancelar no paga; finalizar sí dispara el pago automático
        mvc.perform(post("/vacancies/{id}/applications/{a}/cancel", vacancyId, appAbsent)
                        .header("Authorization", employer.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"ABANDONED\",\"justification\":\"Se fue a mediodía\"}"))
                .andExpect(jsonPath("$.payoutStatus").doesNotExist());
        mvc.perform(post("/vacancies/{id}/applications/{a}/complete", vacancyId, appOk)
                        .header("Authorization", employer.token()))
                .andExpect(jsonPath("$.payoutMode").value("AUTO"))
                .andExpect(jsonPath("$.payoutStatus").value("PROCESSING"));
        mvc.perform(post("/vacancies/{id}/applications/{a}/paid", vacancyId, appOk)
                        .header("Authorization", employer.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":120000}"))
                .andExpect(jsonPath("$.code").value("VAC015_AUTO_PAYOUT_IN_PROGRESS"));

        // Pagos recibe el comando (solo uno, del que finalizó) y responde
        CloudEvent command = awaitOn("payments.co", EventTypes.VACANCY_PAYOUT_REQUESTED, appOk);
        assertThat(command.data()).containsEntry("amountMinor", 120000).containsEntry("currency", "COP")
                .containsEntry("workerId", ok.id());
        java.util.Map<String, Object> reply = new java.util.HashMap<>(command.data());
        reply.put("providerRef", "PO-TEST");
        CloudEvent done = CloudEvent.of("test-payments", EventTypes.PAYMENT_PAYOUT_COMPLETED, "co", appOk, 1, reply,
                java.time.Instant.now(), vacancyId);
        rabbit.convertAndSend(jornal.common.messaging.Topology.EXCHANGE, done.routingKey(), done);

        long deadline = System.currentTimeMillis() + 15_000;
        String body = "";
        while (System.currentTimeMillis() < deadline && !body.contains("\"PAID\"")) {
            body = mvc.perform(get("/vacancies/applications/mine").header("Authorization", ok.token()))
                    .andReturn().getResponse().getContentAsString();
            Thread.sleep(200);
        }
        JsonNode item = json.readTree(body).get("items").get(0);
        assertThat(item.get("payoutStatus").asString()).isEqualTo("PAID");
        assertThat(item.get("paidAmountMinor").asLong()).isEqualTo(120000);
        assertThat(item.get("payoutRef").asString()).isEqualTo("PO-TEST");
    }

    private CloudEvent awaitOn(String queue, String type, String subject) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            CloudEvent e = rabbit.receiveAndConvert(queue, 500, EVENT);
            if (e != null && type.equals(e.type()) && subject.equals(e.subject())) return e;
        }
        throw new AssertionError("No " + type + " for " + subject + " in " + queue);
    }

    // ------------------------------------------------------------------ utilidades

    private String draft() {
        String day = LocalDate.now(ZoneId.of("America/Bogota")).plusDays(3).toString();
        return """
                {"title":"Auxiliar de bodega","description":"Cargar y descargar el camión",
                 "items":["Botas de seguridad","Guantes"],
                 "startLocal":"%sT08:00","endLocal":"%sT16:00","timezone":"America/Bogota",
                 "positions":2,"payMinor":120000,
                 "address":{"street_address":"Calle 26 # 68-35","city":"Bogotá","department":"Cundinamarca"},
                 "latitude":4.6584,"longitude":-74.0937}
                """.formatted(day, day);
    }

    private String apply(User worker, String vacancyId) throws Exception {
        return id(mvc.perform(post("/vacancies/{id}/apply", vacancyId).header("Authorization", worker.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Tengo experiencia\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andReturn().getResponse().getContentAsString());
    }

    private void attendance(User worker, String vacancyId, boolean attending, String expected) throws Exception {
        mvc.perform(post("/vacancies/{id}/attendance", vacancyId).header("Authorization", worker.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"attending\":%s}".formatted(attending)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(expected));
    }

    private void vacancyStatus(User employer, String vacancyId, String expected) throws Exception {
        mvc.perform(get("/vacancies/{id}", vacancyId).header("Authorization", employer.token()))
                .andExpect(jsonPath("$.status").value(expected));
    }

    private CloudEvent await(String type, String vacancyId, Predicate<CloudEvent> match) {
        long deadline = System.currentTimeMillis() + 15_000;
        List<String> seen = new ArrayList<>();
        while (System.currentTimeMillis() < deadline) {
            CloudEvent e = rabbit.receiveAndConvert("notifications.co", 500, EVENT);
            if (e == null) continue;
            seen.add(e.type());
            if (type.equals(e.type()) && vacancyId.equals(e.subject()) && match.test(e)) return e;
        }
        throw new AssertionError("No " + type + " for " + vacancyId + "; seen " + seen);
    }

    private String id(String body) {
        return json.readTree(body).get("id").asString();
    }

    private User worker(double lat, double lon) throws Exception {
        User worker = user("WORKER");
        String category = json.readTree(mvc.perform(get("/categories").param("q", "warehouse")
                        .header("Authorization", worker.token())).andReturn().getResponse().getContentAsString())
                .get(0).get("id").asString();
        mvc.perform(put("/workers/me").header("Authorization", worker.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bio":"Bodega","workRadiusKm":10,"categoryIds":["%s"],
                                 "baseLatitude":%s,"baseLongitude":%s}
                                """.formatted(category, lat, lon)))
                .andExpect(status().isOk());
        return worker;
    }

    private User user(String rol) throws Exception {
        String email = "vac-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"Clave12345","givenName":"Ana","familyName":"Prueba",
                         "rol":"%s","acceptedTerms":true}
                        """.formatted(email, rol)))
                .andExpect(status().isCreated());
        JsonNode login = json.readTree(mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Clave12345\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString());
        String token = "Bearer " + login.get("accessToken").asString();
        String me = mvc.perform(get("/users/me").header("Authorization", token))
                .andReturn().getResponse().getContentAsString();
        return new User(json.readTree(me).get("id").asString(), token);
    }
}
