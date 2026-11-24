package jornal.dispatch;

import jornal.common.events.CloudEvent;
import jornal.common.events.EventTypes;
import jornal.common.messaging.Topology;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F6: asignación por oferta secuencial, reserva atómica y seguimiento en vivo. La prueba hace de core
 * (publica fotos de trabajadores y pedidos, lee las respuestas en {@code core.co}). Coordenadas de Bogotá.
 */
@SpringBootTest(properties = "jornal.dispatch.search-timeout=1s")
@Import(DispatchFlowTest.Containers.class)
@AutoConfigureMockMvc
class DispatchFlowTest {

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

        @Bean @ServiceConnection(name = "redis")
        GenericContainer<?> redis() {
            return new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);
        }
    }

    private static final ParameterizedTypeReference<CloudEvent> EVENT = new ParameterizedTypeReference<>() { };
    private static final double LAT = 4.6584, LON = -74.0937;          // lugar del trabajo

    @Autowired MockMvc mvc;
    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitAdmin admin;
    @Autowired DispatchRepository repository;
    @Autowired DispatchService dispatch;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;

    @org.springframework.beans.factory.annotation.Value("${jornal.security.jwt.secret}") String secret;
    @org.springframework.beans.factory.annotation.Value("${jornal.security.jwt.issuer}") String issuer;

    @Test
    void nearestWorkerGetsTheOfferAndAcceptingAssignsIt() throws Exception {
        UUID category = UUID.randomUUID();
        UUID near = worker(category, LAT + 0.009, LON, 10, 4.5);           // ~1 km
        UUID far = worker(category, LAT + 0.045, LON, 10, 5.0);            // ~5 km
        worker(UUID.randomUUID(), LAT, LON, 10, 5.0);                      // otra categoría
        worker(category, LAT + 0.30, LON, 10, 5.0);                        // ~33 km: fuera de su radio
        UUID request = request(category);

        String offerId = awaitOffer(near);
        mvc.perform(get("/offers/me").header("Authorization", token(far, "WORKER")))
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(post("/offers/{id}/accept", offerId).header("Authorization", token(near, "WORKER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));
        CloudEvent assigned = await("core.co", EventTypes.DISPATCH_WORKER_ASSIGNED, request);
        assertThat(assigned.data()).containsEntry("workerId", near.toString());
        assertThat(repository.countReservations(near)).isEqualTo(1);
    }

    @Test
    void rejectionMovesTheOfferToTheNextCandidate() throws Exception {
        UUID category = UUID.randomUUID();
        UUID first = worker(category, LAT + 0.009, LON, 10, 4.0);
        UUID second = worker(category, LAT + 0.027, LON, 10, 4.0);
        request(category);

        String offer = awaitOffer(first);
        mvc.perform(post("/offers/{id}/reject", offer).header("Authorization", token(first, "WORKER")))
                .andExpect(status().isOk());
        awaitOffer(second);
    }

    @Test
    void expiredOfferMovesToTheNextCandidate() throws Exception {
        UUID category = UUID.randomUUID();
        UUID first = worker(category, LAT + 0.009, LON, 10, 4.0);
        UUID second = worker(category, LAT + 0.027, LON, 10, 4.0);
        request(category);
        awaitOffer(first);

        assertThat(dispatch.expireOffers(Instant.now().plus(Duration.ofMinutes(2)))).isPositive();
        awaitOffer(second);
    }

    @Test
    void oneWorkerTwoSimultaneousRequestsLeavesOneReservation() throws Exception {
        UUID category = UUID.randomUUID();
        UUID only = worker(category, LAT, LON, 10, 4.0);
        UUID r1 = UUID.randomUUID();
        UUID r2 = UUID.randomUUID();
        send(EventTypes.REQUEST_ASSIGNMENT_REQUESTED, r1, requestData(r1, category));
        send(EventTypes.REQUEST_ASSIGNMENT_REQUESTED, r2, requestData(r2, category));

        String offer = awaitOffer(only);                                   // una sola oferta pendiente a la vez
        mvc.perform(post("/offers/{id}/accept", offer).header("Authorization", token(only, "WORKER")))
                .andExpect(status().isOk());
        assertThat(repository.countReservations(only)).isEqualTo(1);

        // El otro pedido no encuentra candidatos libres: vence la búsqueda y se informa al core
        UUID loser = repository.reservedRequestOf(only).orElseThrow().equals(r1) ? r2 : r1;
        CloudEvent failed = await("core.co", EventTypes.DISPATCH_ASSIGNMENT_FAILED, loser);
        assertThat(failed.data()).containsEntry("reason", "NO_WORKER_NEARBY");
        assertThat(repository.countReservations(only)).isEqualTo(1);
    }

    @Test
    void reservationIsAtomicUnderConcurrency() throws Exception {
        UUID worker = UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            UUID request = UUID.randomUUID();
            results.add(pool.submit(() -> {
                start.await();
                return repository.reserve(worker, "co", request);
            }));
        }
        start.countDown();
        int winners = 0;
        for (Future<Boolean> f : results) if (f.get()) winners++;
        pool.shutdown();
        assertThat(winners).isEqualTo(1);
        assertThat(repository.countReservations(worker)).isEqualTo(1);
    }

    @Test
    void clientSeesTheWorkerMoving() throws Exception {
        UUID category = UUID.randomUUID();
        UUID w = worker(category, LAT + 0.009, LON, 10, 4.0);
        UUID client = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        Map<String, Object> data = requestData(request, category);
        data.put("clientId", client.toString());
        send(EventTypes.REQUEST_ASSIGNMENT_REQUESTED, request, data);
        mvc.perform(post("/offers/{id}/accept", awaitOffer(w)).header("Authorization", token(w, "WORKER")))
                .andExpect(status().isOk());

        // Cola temporal en el exchange no durable de posiciones en vivo
        org.springframework.amqp.core.Queue liveQueue = new AnonymousQueue();
        admin.declareQueue(liveQueue);
        admin.declareBinding(BindingBuilder.bind(liveQueue)
                .to(new TopicExchange(Topology.LIVE_EXCHANGE, false, false)).with("co.#"));

        for (double lat : new double[]{LAT + 0.006, LAT + 0.003}) {
            mvc.perform(put("/locations/me").header("Authorization", token(w, "WORKER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"latitude\":%s,\"longitude\":%s}".formatted(lat, LON)))
                    .andExpect(status().isOk());
            mvc.perform(get("/tracking/{id}", request).header("Authorization", token(client, "APPLICANT")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ASSIGNED"))
                    .andExpect(jsonPath("$.position.latitude").value(lat));
        }
        CloudEvent live = rabbit.receiveAndConvert(liveQueue.getName(), 5_000, EVENT);
        assertThat(live).isNotNull();
        assertThat(live.type()).isEqualTo(EventTypes.DISPATCH_WORKER_LOCATION);
        assertThat(live.data()).containsEntry("requestId", request.toString());

        mvc.perform(get("/tracking/{id}", request).header("Authorization", token(UUID.randomUUID(), "APPLICANT")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/tracking/{id}", request)).andExpect(status().isUnauthorized());
    }

    @Test
    void cancellationReleasesTheWorker() throws Exception {
        UUID category = UUID.randomUUID();
        UUID w = worker(category, LAT, LON, 10, 4.0);
        UUID request = request(category);
        mvc.perform(post("/offers/{id}/accept", awaitOffer(w)).header("Authorization", token(w, "WORKER")))
                .andExpect(status().isOk());
        assertThat(repository.countReservations(w)).isEqualTo(1);

        send(EventTypes.REQUEST_CANCELLED, request, Map.of("requestId", request.toString(), "reason", "ADMIN"));
        waitUntil(() -> repository.countReservations(w) == 0);
    }

    @Test
    void olderWorkerSnapshotsAreIgnored() {
        UUID w = UUID.randomUUID();
        var newer = new DispatchRepository.WorkerRow("co", w, "Nuevo", true, true, Set.of(), 10, LAT, LON, 4, 1, 200);
        var older = new DispatchRepository.WorkerRow("co", w, "Viejo", true, false, Set.of(), 10, LAT, LON, 4, 1, 100);
        assertThat(repository.upsertWorker(newer)).isTrue();
        assertThat(repository.upsertWorker(older)).isFalse();
        assertThat(repository.worker("co", w)).get().extracting(DispatchRepository.WorkerRow::name).isEqualTo("Nuevo");
    }

    // ------------------------------------------------------------------ utilidades

    private UUID worker(UUID category, double lat, double lon, int radiusKm, double rating) {
        UUID id = UUID.randomUUID();
        Map<String, Object> data = new HashMap<>();
        data.put("workerId", id.toString());
        data.put("name", "Trabajador " + id.toString().substring(0, 4));
        data.put("eligible", true);
        data.put("available", true);
        data.put("categoryIds", List.of(category.toString()));
        data.put("workRadiusKm", radiusKm);
        data.put("baseLatitude", lat);
        data.put("baseLongitude", lon);
        data.put("ratingAvg", rating);
        data.put("ratingCount", 3);
        send(EventTypes.WORKER_UPDATED, id, data);
        waitUntil(() -> repository.worker("co", id).isPresent());
        return id;
    }

    private UUID request(UUID category) {
        UUID id = UUID.randomUUID();
        send(EventTypes.REQUEST_ASSIGNMENT_REQUESTED, id, requestData(id, category));
        return id;
    }

    private static Map<String, Object> requestData(UUID request, UUID category) {
        Map<String, Object> d = new HashMap<>();
        d.put("requestId", request.toString());
        d.put("clientId", UUID.randomUUID().toString());
        d.put("categoryId", category.toString());
        d.put("categoryName", "Bodega e inventario");
        d.put("addressText", "Calle 26 # 68-35, Bogotá, Cundinamarca");
        d.put("latitude", LAT);
        d.put("longitude", LON);
        d.put("scheduledAt", Instant.now().plus(Duration.ofDays(2)).toString());
        return d;
    }

    private void send(String type, UUID subject, Map<String, Object> data) {
        CloudEvent e = CloudEvent.of("jornal-core-co", type, "co", subject.toString(), System.nanoTime(), data,
                Instant.now(), subject.toString());
        rabbit.convertAndSend(Topology.EXCHANGE, e.routingKey(), e);
    }

    private String awaitOffer(UUID worker) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            String raw = mvc.perform(get("/offers/me").header("Authorization", token(worker, "WORKER")))
                    .andReturn().getResponse().getContentAsString();
            JsonNode offers = json.readTree(raw);
            if (offers.size() > 0) return offers.get(0).get("offerId").asString();
            Thread.sleep(200);
        }
        throw new AssertionError("No offer for worker " + worker);
    }

    private CloudEvent await(String queue, String type, UUID request) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            CloudEvent e = rabbit.receiveAndConvert(queue, 300, EVENT);
            if (e != null && type.equals(e.type()) && request.toString().equals(e.subject())) return e;
        }
        throw new AssertionError("No " + type + " for " + request + " in " + queue);
    }

    private String token(UUID user, String role) {
        Instant now = Instant.now();
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).issuedAt(now)
                .expiresAt(now.plusSeconds(600)).subject(user.toString())
                .claim("role", role).claim("region", "CO").build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        assertThat(condition.getAsBoolean()).as("condition within 15 s").isTrue();
    }
}
