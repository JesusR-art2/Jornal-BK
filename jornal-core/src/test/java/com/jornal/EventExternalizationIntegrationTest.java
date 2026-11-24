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
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F3: un cambio en el core sale por el Outbox hacia RabbitMQ con sobre CloudEvents y llega a las colas
 * de los 4 subsistemas de la región. Mismas propiedades que {@link LocalizationIntegrationTest}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "jornal.region.code=CO",
        "jornal.admin.email=admin.co@example.test",
        "jornal.admin.password=AdminPass123"})
@AutoConfigureMockMvc
class EventExternalizationIntegrationTest {

    private static final ParameterizedTypeReference<CloudEvent> EVENT = new ParameterizedTypeReference<>() { };

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired RabbitTemplate rabbit;
    @Autowired JdbcTemplate jdbc;

    @Test
    void profileChangeReachesEverySubsystemQueueOfTheRegion() throws Exception {
        long newVersion = updateProfile();

        for (String subsystem : List.of("payments", "dispatch", "notifications", "billing")) {
            CloudEvent event = receiveVersion(subsystem + ".co", newVersion);
            assertThat(event).as("message in %s.co", subsystem).isNotNull();
            assertThat(event.specversion()).isEqualTo("1.0");
            assertThat(event.type()).isEqualTo(EventTypes.REGION_PROFILE_UPDATED);
            assertThat(event.source()).isEqualTo("jornal-core-co");
            assertThat(event.region()).isEqualTo("co");
            assertThat(event.subject()).isEqualTo("CO");
            assertThat(event.data()).containsEntry("currency", "COP");
        }
        // Nada para otras regiones
        assertThat(rabbit.receive("payments.br", 200)).isNull();

        // El Outbox quedó completo
        Integer pending = jdbc.queryForObject(
                "SELECT count(*) FROM event_publication WHERE completion_date IS NULL", Integer.class);
        assertThat(pending).isZero();
    }

    private long updateProfile() throws Exception {
        String admin = login();
        String raw = mvc.perform(get("/region-profile")).andReturn().getResponse().getContentAsString();
        ObjectNode body = (ObjectNode) json.readTree(raw);
        body.remove("code");
        body.remove("updatedAt");
        body.put("travelFeeMinor", body.get("travelFeeMinor").asLong() + 1);
        String updated = mvc.perform(put("/admin/region-profile").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(updated).get("version").asLong();
    }

    /** Las colas se comparten con otras pruebas del mismo contexto: busca el mensaje de esta versión. */
    private CloudEvent receiveVersion(String queue, long version) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            CloudEvent event = rabbit.receiveAndConvert(queue, 1_000, EVENT);
            if (event != null && event.aggregateversion() == version) return event;
        }
        return null;
    }

    private String login() throws Exception {
        String raw = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin.co@example.test\",\"password\":\"AdminPass123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(raw).get("accessToken").asString();
    }
}
