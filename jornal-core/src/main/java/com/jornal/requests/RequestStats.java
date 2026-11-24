package com.jornal.requests;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pedidos por estado de esta región: métrica {@code jornal_requests{status, region}} (se refresca cada 15 s)
 * y datos del tablero de administración.
 */
@Component
public class RequestStats {

    private final ServiceRequestRepository repository;
    private final Map<RequestStatus, AtomicLong> byStatus = new EnumMap<>(RequestStatus.class);

    public RequestStats(ServiceRequestRepository repository, MeterRegistry registry,
                        @Value("${jornal.region.code}") String region) {
        this.repository = repository;
        for (RequestStatus status : RequestStatus.values()) {
            AtomicLong value = new AtomicLong();
            byStatus.put(status, value);
            Gauge.builder("jornal.requests", value, AtomicLong::get)
                    .description("Pedidos por estado")
                    .tag("status", status.name())
                    .tag("region", region.toLowerCase(Locale.ROOT))
                    .register(registry);
        }
    }

    @Scheduled(fixedDelayString = "${jornal.metrics.refresh-every:15s}", initialDelayString = "5s")
    @Transactional(readOnly = true)
    public void refresh() {
        countByStatus().forEach((status, n) -> byStatus.get(RequestStatus.valueOf(status)).set(n));
    }

    @Transactional(readOnly = true)
    public Map<String, Long> countByStatus() {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (RequestStatus status : RequestStatus.values()) counts.put(status.name(), 0L);
        for (Object[] row : repository.countGroupedByStatus()) {
            counts.put(((RequestStatus) row[0]).name(), ((Number) row[1]).longValue());
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public long createdSince(Instant since) {
        return repository.countByCreatedAtGreaterThanEqual(since);
    }

    @Transactional(readOnly = true)
    public long reachedSince(String status, Instant since) {
        return repository.countByStatusAndUpdatedAtGreaterThanEqual(RequestStatus.valueOf(status), since);
    }
}
