package jornal.notifications;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jornal.common.events.CloudEvent;
import jornal.common.events.EventTypes;
import jornal.common.messaging.Topology;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * F7: cada cambio de estado llega al cliente y al trabajador en su idioma y su zona horaria, por la app
 * (WebSocket con JWT y permiso por tema), correo, y SMS y push simulados. La prueba hace de core:
 * publica los eventos en RabbitMQ y responde el contacto (simulado).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(NotificationsFlowTest.Containers.class)
class NotificationsFlowTest {

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

    private static final String SCHEDULED = "2026-12-01T13:00:00Z";   // 08:00 en Bogotá

    @LocalServerPort int port;
    @Autowired RabbitTemplate rabbit;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ContactClient contacts;
    @MockitoBean JavaMailSender mail;

    @Value("${jornal.security.jwt.secret}") String secret;
    @Value("${jornal.security.jwt.issuer}") String issuer;

    private UUID client;
    private UUID worker;

    @BeforeEach
    void people() {
        client = UUID.randomUUID();
        worker = UUID.randomUUID();
        when(contacts.find(eq("co"), eq(client))).thenReturn(Optional.of(new ContactClient.Contact(
                client, "Ana Prueba", "ana@example.test", null, "es-CO", "America/Bogota", true)));
        // El trabajador usa la app en inglés: recibe en inglés aunque el pedido sea en Colombia
        when(contacts.find(eq("co"), eq(worker))).thenReturn(Optional.of(new ContactClient.Contact(
                worker, "Luis Prueba", "luis@example.test", "+573001234567", "en-US", "America/Bogota", true)));
    }

    @Test
    void eachStatusChangeReachesClientAndWorkerInTheirLanguage() throws Exception {
        UUID request = UUID.randomUUID();
        StompSession clientSession = connect(client);
        BlockingQueue<Map<String, Object>> clientInbox = subscribe(clientSession, RequestTopics.user(client));
        StompSession workerSession = connect(worker);
        BlockingQueue<Map<String, Object>> workerInbox = subscribe(workerSession, RequestTopics.user(worker));

        publish(statusChanged(request, "SEARCHING_WORKER", "ASSIGNED", worker));

        Map<String, Object> toClient = next(clientInbox);
        assertThat(toClient.get("title")).isEqualTo("Trabajador asignado");
        assertThat((String) toClient.get("body")).contains("Bodega").contains("8:00");
        Map<String, Object> toWorker = next(workerInbox);
        assertThat(toWorker.get("title")).isEqualTo("Job assigned");
        assertThat((String) toWorker.get("body")).contains("8:00");

        // Correo a cada uno en su idioma
        ArgumentCaptor<SimpleMailMessage> emails = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, timeout(10_000).atLeast(2)).send(emails.capture());
        assertThat(emails.getAllValues()).extracting(SimpleMailMessage::getSubject)
                .contains("Trabajador asignado", "Job assigned");

        // Registro: 4 canales por persona; el cliente no tiene teléfono (SMS omitido)
        waitUntil(() -> count(request) == 8);
        assertThat(jdbc.queryForList("""
                SELECT channel || ':' || status FROM notifications WHERE recipient_id = ? ORDER BY channel
                """, String.class, client)).containsExactly("EMAIL:SENT", "IN_APP:SENT", "PUSH:SENT", "SMS:SKIPPED");

        // Siguiente cambio de estado: ambos lo reciben
        publish(statusChanged(request, "ASSIGNED", "EN_ROUTE", worker));
        assertThat(next(clientInbox).get("title")).isEqualTo("Tu trabajador va en camino");
        assertThat(next(workerInbox).get("title")).isEqualTo("On your way");
    }

    @Test
    void duplicateEventIsNotifiedOnce() throws Exception {
        UUID request = UUID.randomUUID();
        CloudEvent event = statusChanged(request, "PENDING_PAYMENT", "SEARCHING_WORKER", null);
        publish(event);
        publish(event);
        waitUntil(() -> count(request) == 3);
        Thread.sleep(1000);
        assertThat(count(request)).isEqualTo(3);   // solo el cliente: IN_APP, PUSH y EMAIL
    }

    @Test
    void workerGetsTheOfferWithACountdown() throws Exception {
        UUID request = UUID.randomUUID();
        Instant now = Instant.now();
        Map<String, Object> data = new HashMap<>();
        data.put("requestId", request.toString());
        data.put("offerId", UUID.randomUUID().toString());
        data.put("workerId", worker.toString());
        data.put("expiresAt", now.plusSeconds(45).toString());
        data.put("distanceKm", 1.3);
        data.put("categoryName", "Bodega");
        data.put("addressText", "Calle 26 # 68-35, Bogotá");
        data.put("scheduledAt", SCHEDULED);
        publish(CloudEvent.of("jornal-dispatch", EventTypes.DISPATCH_OFFER_CREATED, "co", request.toString(), 1,
                data, now, request.toString()));

        waitUntil(() -> count(request) == 4);   // IN_APP, PUSH, EMAIL y SMS
        String body = jdbc.queryForObject(
                "SELECT body FROM notifications WHERE request_id = ? AND channel = 'IN_APP'", String.class, request);
        assertThat(body).contains("1.3 km").contains("45 seconds to accept");
    }

    @Test
    void topicsAreOnlyForTheirOwners() throws Exception {
        UUID request = UUID.randomUUID();
        publish(statusChanged(request, "SEARCHING_WORKER", "ASSIGNED", worker));
        waitUntil(() -> count(request) == 8);

        // Otro usuario no puede escuchar los avisos del cliente ni el pedido ajeno
        UUID stranger = UUID.randomUUID();
        assertThat(subscribeIsRejected(stranger, RequestTopics.user(client))).isTrue();
        assertThat(subscribeIsRejected(stranger, RequestTopics.request(request))).isTrue();
        assertThat(subscribeIsRejected(stranger, "/topic/anything")).isTrue();

        // Sin token no hay conexión
        assertThatConnectFails(null);
        assertThatConnectFails("Bearer not-a-token");

        // El cliente sigue la posición en vivo del trabajador por el tema del pedido
        StompSession session = connect(client);
        BlockingQueue<Map<String, Object>> live = subscribe(session, RequestTopics.request(request));
        CloudEvent location = CloudEvent.of("jornal-dispatch", EventTypes.DISPATCH_WORKER_LOCATION, "co",
                worker.toString(), 1, Map.of("workerId", worker.toString(), "requestId", request.toString(),
                        "latitude", 4.66, "longitude", -74.09, "at", Instant.now().toString()),
                Instant.now(), request.toString());
        rabbit.convertAndSend(Topology.LIVE_EXCHANGE, "co.worker-location", location);
        Map<String, Object> position = next(live);
        assertThat(position.get("type")).isEqualTo("location");
        assertThat(position.get("latitude")).isEqualTo(4.66);
    }

    // ------------------------------------------------------------------ ayudas

    private CloudEvent statusChanged(UUID request, String from, String to, UUID workerId) {
        Map<String, Object> data = new HashMap<>();
        data.put("requestId", request.toString());
        data.put("clientId", client.toString());
        data.put("workerId", workerId == null ? null : workerId.toString());
        data.put("from", from);
        data.put("to", to);
        data.put("categoryName", "Bodega");
        data.put("scheduledAt", SCHEDULED);
        data.put("timezone", "America/Bogota");
        return CloudEvent.of("jornal-core-co", EventTypes.REQUEST_STATUS_CHANGED, "co", request.toString(), 1,
                data, Instant.now(), request.toString());
    }

    private void publish(CloudEvent event) {
        rabbit.convertAndSend(Topology.EXCHANGE, event.routingKey(), event);
    }

    private int count(UUID request) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM notifications WHERE request_id = ?", Integer.class,
                request);
        return n == null ? 0 : n;
    }

    private WebSocketStompClient stompClient() {
        WebSocketStompClient stomp = new WebSocketStompClient(new StandardWebSocketClient());
        stomp.setMessageConverter(new JacksonJsonMessageConverter());
        return stomp;
    }

    private StompSession connect(UUID user) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", token(user));
        return stompClient().connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
                new StompSessionHandlerAdapter() { }).get(10, TimeUnit.SECONDS);
    }

    private void assertThatConnectFails(String authorization) {
        StompHeaders headers = new StompHeaders();
        if (authorization != null) headers.add("Authorization", authorization);
        CompletableFuture<Boolean> failed = new CompletableFuture<>();
        stompClient().connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
                new FailureHandler(failed));
        assertThat(failed).succeedsWithin(10, TimeUnit.SECONDS);
    }

    private boolean subscribeIsRejected(UUID user, String destination) throws Exception {
        CompletableFuture<Boolean> failed = new CompletableFuture<>();
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", token(user));
        StompSession session = stompClient().connectAsync("ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(), headers, new FailureHandler(failed)).get(10, TimeUnit.SECONDS);
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders h) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders h, Object payload) {
                failed.complete(false);
            }
        });
        return failed.get(10, TimeUnit.SECONDS);
    }

    /** Termina la espera con {@code true} ante un ERROR de STOMP o el cierre de la conexión. */
    private static final class FailureHandler extends StompSessionHandlerAdapter {
        private final CompletableFuture<Boolean> failed;

        FailureHandler(CompletableFuture<Boolean> failed) {
            this.failed = failed;
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return byte[].class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            failed.complete(true);
        }

        @Override
        public void handleException(StompSession s, StompCommand c, StompHeaders h, byte[] p, Throwable e) {
            failed.complete(true);
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            failed.complete(true);
        }
    }

    @SuppressWarnings("unchecked")
    private BlockingQueue<Map<String, Object>> subscribe(StompSession session, String destination)
            throws InterruptedException {
        BlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((Map<String, Object>) payload);
            }
        });
        Thread.sleep(500);   // el servidor registra la suscripción antes de publicar
        return queue;
    }

    private static Map<String, Object> next(BlockingQueue<Map<String, Object>> queue) throws InterruptedException {
        Map<String, Object> message = queue.poll(15, TimeUnit.SECONDS);
        assertThat(message).as("message within 15 s").isNotNull();
        return message;
    }

    private String token(UUID user) {
        Instant now = Instant.now();
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).issuedAt(now)
                .expiresAt(now.plusSeconds(600)).subject(user.toString())
                .claim("role", "APPLICANT").claim("region", "CO").build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(150);
        }
        assertThat(condition.getAsBoolean()).as("condition within 15 s").isTrue();
    }
}
