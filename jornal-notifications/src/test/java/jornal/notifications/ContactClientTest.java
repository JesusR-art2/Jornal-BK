package jornal.notifications;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El contacto se pide al core con reintento: un fallo pasajero no pierde el aviso. */
class ContactClientTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void retriesUntilTheCoreAnswers() throws IOException {
        UUID id = UUID.randomUUID();
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger tokenOk = new AtomicInteger();
        start(exchange -> {
            if ("secret".equals(exchange.getRequestHeaders().getFirst("X-Internal-Token"))) tokenOk.incrementAndGet();
            if (calls.incrementAndGet() < 3) {
                exchange.sendResponseHeaders(503, -1);
            } else {
                byte[] body = ("{\"id\":\"" + id + "\",\"name\":\"Ana Prueba\",\"email\":\"ana@example.test\","
                        + "\"phone\":null,\"locale\":\"es-CO\",\"timezone\":\"America/Bogota\",\"active\":true}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        ContactClient.Contact contact = client(3).find("co", id).orElseThrow();
        assertThat(contact.email()).isEqualTo("ana@example.test");
        assertThat(calls.get()).isEqualTo(3);
        assertThat(tokenOk.get()).isEqualTo(3);
    }

    @Test
    void unknownUserIsEmptyAndADownCoreFails() throws IOException {
        start(exchange -> {
            exchange.sendResponseHeaders(exchange.getRequestURI().getPath().endsWith("missing") ? 503 : 404, -1);
            exchange.close();
        });
        assertThat(client(2).find("co", UUID.randomUUID())).isEmpty();
        HttpServer down = server;
        down.stop(0);
        server = null;
        assertThatThrownBy(() -> client(2).find("co", UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not available");
    }

    private void start(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.start();
    }

    private ContactClient client(int attempts) {
        int port = server == null ? 1 : server.getAddress().getPort();
        return new ContactClient(new NotificationProperties("http://127.0.0.1:" + port, "secret", attempts,
                Duration.ofMillis(10), "test@jornal.test"));
    }
}
