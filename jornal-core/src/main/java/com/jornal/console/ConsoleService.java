package com.jornal.console;

import com.jornal.region.RegionApi;
import com.jornal.region.RegionProfileView;
import com.jornal.requests.RequestStats;
import com.jornal.workers.WorkerApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Consola de administración: tablero de la región, salud de cada servicio (consulta su {@code /actuator/health})
 * y estado de las colas (API de gestión de RabbitMQ). Un servicio que no responde en 2 s aparece {@code DOWN}.
 */
@Service
@EnableConfigurationProperties(ConsoleProperties.class)
public class ConsoleService {

    private final RequestStats requests;
    private final WorkerApi workers;
    private final RegionApi region;
    private final ConsoleProperties properties;
    private final Clock clock;
    private final RestClient http;

    public ConsoleService(RequestStats requests, WorkerApi workers, RegionApi region, ConsoleProperties properties,
                          Clock clock) {
        this.requests = requests;
        this.workers = workers;
        this.region = region;
        this.properties = properties;
        this.clock = clock;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    /** Pedidos por estado, actividad del día (en la zona del país) y trabajadores disponibles. */
    public Map<String, Object> dashboard() {
        RegionProfileView profile = region.current();
        ZoneId zone = ZoneId.of(profile.defaultTimezone());
        Instant startOfDay = LocalDate.now(clock.withZone(zone)).atStartOfDay(zone).toInstant();
        Map<String, Object> today = new LinkedHashMap<>();
        today.put("since", startOfDay.toString());
        today.put("created", requests.createdSince(startOfDay));
        today.put("completed", requests.reachedSince("COMPLETED", startOfDay));
        today.put("cancelled", requests.reachedSince("CANCELLED", startOfDay));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("region", profile.code());
        out.put("currency", profile.currency());
        out.put("timezone", profile.defaultTimezone());
        out.put("generatedAt", clock.instant().toString());
        out.put("requestsByStatus", requests.countByStatus());
        out.put("today", today);
        out.put("workersAvailable", workers.countAvailable());
        return out;
    }

    public record ServiceHealth(String name, String status, Long latencyMs, String detail) {
    }

    /** Salud de cada servicio configurado, en paralelo; los caídos primero. */
    public List<ServiceHealth> services() {
        List<CompletableFuture<ServiceHealth>> checks = properties.serviceUrls().entrySet().stream()
                .map(e -> CompletableFuture.supplyAsync(() -> check(e.getKey(), e.getValue())))
                .toList();
        List<ServiceHealth> result = new ArrayList<>(checks.stream().map(CompletableFuture::join).toList());
        result.sort(Comparator.comparing((ServiceHealth h) -> "UP".equals(h.status())).thenComparing(ServiceHealth::name));
        return result;
    }

    private ServiceHealth check(String name, String url) {
        long start = System.nanoTime();
        try {
            Map<?, ?> body = http.get().uri(url).retrieve().body(Map.class);
            long ms = (System.nanoTime() - start) / 1_000_000;
            String status = body == null ? "UNKNOWN" : String.valueOf(body.get("status"));
            return new ServiceHealth(name, status, ms, null);
        } catch (RestClientResponseException e) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            return new ServiceHealth(name, "DOWN", ms, "HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            return new ServiceHealth(name, "DOWN", null, rootMessage(e));
        }
    }

    /** Colas de RabbitMQ: mensajes listos, sin confirmar y consumidores. Marca las colas sin consumidor. */
    public Map<String, Object> queues() {
        Map<String, Object> out = new LinkedHashMap<>();
        if (properties.rabbitApi() == null || properties.rabbitApi().isBlank()) {
            out.put("available", false);
            out.put("detail", "jornal.console.rabbit-api is not configured");
            return out;
        }
        String auth = Base64.getEncoder().encodeToString((properties.rabbitUser() + ":" + properties.rabbitPassword())
                .getBytes(StandardCharsets.UTF_8));
        try {
            List<?> raw = http.get().uri(properties.rabbitApi() + "/queues")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + auth)
                    .retrieve().body(List.class);
            List<Map<String, Object>> queues = new ArrayList<>();
            long deadLetters = 0;
            for (Object item : raw == null ? List.of() : raw) {
                Map<?, ?> q = (Map<?, ?>) item;
                String name = String.valueOf(q.get("name"));
                if (name.startsWith("amq.") || name.startsWith("spring.gen")) continue;   // colas temporales
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", name);
                row.put("messages", number(q.get("messages")));
                row.put("ready", number(q.get("messages_ready")));
                row.put("unacked", number(q.get("messages_unacknowledged")));
                row.put("consumers", number(q.get("consumers")));
                row.put("noConsumer", number(q.get("consumers")) == 0 && !name.equals("jornal.dead-letter"));
                if (name.equals("jornal.dead-letter")) deadLetters = number(q.get("messages"));
                queues.add(row);
            }
            out.put("available", true);
            out.put("deadLetters", deadLetters);
            out.put("queues", queues);
        } catch (RestClientException e) {
            out.put("available", false);
            out.put("detail", rootMessage(e));
        }
        return out;
    }

    private static long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0;
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) t = t.getCause();
        return t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
    }
}
