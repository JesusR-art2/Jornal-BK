package jornal.payments;

import jornal.common.events.CloudEvent;
import jornal.common.events.EventTypes;
import jornal.common.messaging.Topology;
import jornal.common.subsystem.Inbox;
import jornal.common.subsystem.RegionProfileCache;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/** F3: pagos consume su cola regional y procesa cada mensaje una sola vez aunque se reenvíe. */
@SpringBootTest
@Import(PaymentsMessagingTest.Containers.class)
class PaymentsMessagingTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer(DockerImageName.parse("postgres:16"));
        }

        @Bean
        @ServiceConnection
        RabbitMQContainer rabbit() {
            return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management"));
        }
    }

    @Autowired RabbitTemplate rabbit;
    @Autowired Inbox inbox;
    @Autowired RegionProfileCache cache;
    @Autowired JdbcTemplate jdbc;

    @Test
    void duplicatedMessageIsProcessedOnceAndOldVersionsAreIgnored() throws Exception {
        long before = inbox.count();      // las pruebas comparten la base
        CloudEvent v3 = profileEvent(3);
        send(v3);
        send(v3);                     // reenvío del mismo mensaje (mismo id)
        send(profileEvent(2));        // versión vieja que llega tarde

        waitUntil(() -> inbox.count() == before + 2);
        Thread.sleep(500);            // margen por si el duplicado se procesara tarde

        assertThat(inbox.count()).isEqualTo(before + 2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inbox WHERE message_id = ?", Integer.class, v3.id()))
                .isEqualTo(1);
        assertThat(cache.entries()).filteredOn(e -> e.region().equals("co")).singleElement()
                .satisfies(e -> assertThat(e.version()).isEqualTo(3));
        assertThat(cache.payload("co")).containsEntry("currency", "COP");
    }

    @Test
    void listensToEveryRegionQueue() throws Exception {
        long before = inbox.count();
        send(CloudEvent.of("jornal-core-jp", EventTypes.REGION_PROFILE_UPDATED, "jp", "JP", 1,
                Map.of("currency", "JPY"), Instant.now(), null));
        waitUntil(() -> inbox.count() == before + 1);
        assertThat(cache.payload("jp")).containsEntry("currency", "JPY");
    }

    private static CloudEvent profileEvent(long version) {
        return CloudEvent.of("jornal-core-co", EventTypes.REGION_PROFILE_UPDATED, "co", "CO", version,
                Map.of("code", "CO", "currency", "COP", "version", version), Instant.now(), null);
    }

    private void send(CloudEvent event) {
        rabbit.convertAndSend(Topology.EXCHANGE, event.routingKey(), event);
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }
        assertThat(condition.getAsBoolean()).as("condition within 15 s").isTrue();
    }
}
