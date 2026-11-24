package jornal.dispatch;

import jornal.common.events.CloudEvent;
import jornal.common.events.EventTypes;
import jornal.common.messaging.Topology;
import jornal.common.subsystem.SubsystemOutbox;
import jornal.dispatch.DispatchRepository.Assignment;
import jornal.dispatch.DispatchRepository.Offer;
import jornal.dispatch.DispatchRepository.WorkerRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

/**
 * Asignación por oferta automática secuencial (D7): se ofrece el pedido al mejor candidato con un plazo; si no
 * acepta (rechaza o vence), al siguiente. Al aceptar se reserva al trabajador de forma atómica y se responde
 * {@code jornal.dispatch.worker-assigned.v1}; sin candidatos o tras {@code maxOffers}, {@code assignment-failed}.
 */
@Service
public class DispatchService {

    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);
    private static final Set<String> TERMINAL = Set.of("COMPLETED", "CANCELLED", "EXPIRED", "FAILED");

    private final DispatchRepository repository;
    private final Matching matching;
    private final LiveLocations live;
    private final SubsystemOutbox outbox;
    private final RabbitTemplate rabbit;
    private final DispatchProperties properties;

    public DispatchService(DispatchRepository repository, Matching matching, LiveLocations live,
                           SubsystemOutbox outbox, RabbitTemplate rabbit, DispatchProperties properties) {
        this.repository = repository;
        this.matching = matching;
        this.live = live;
        this.outbox = outbox;
        this.rabbit = rabbit;
        this.properties = properties;
    }

    // ------------------------------------------------------------------ eventos del core

    @Transactional
    public void onWorkerUpdated(CloudEvent e) {
        Map<String, Object> d = e.data();
        List<?> categories = d.get("categoryIds") instanceof List<?> l ? l : List.of();
        Set<UUID> categoryIds = new HashSet<>();
        categories.forEach(c -> categoryIds.add(UUID.fromString(c.toString())));
        repository.upsertWorker(new WorkerRow(e.region(), UUID.fromString(d.get("workerId").toString()),
                (String) d.get("name"), bool(d.get("eligible")), bool(d.get("available")), categoryIds,
                ((Number) d.get("workRadiusKm")).intValue(), dbl(d.get("baseLatitude")), dbl(d.get("baseLongitude")),
                dbl(d.get("ratingAvg")) == null ? 0 : dbl(d.get("ratingAvg")),
                d.get("ratingCount") == null ? 0 : ((Number) d.get("ratingCount")).intValue(), e.aggregateversion()));
    }

    @Transactional
    public void onAssignmentRequested(CloudEvent e) {
        Map<String, Object> d = e.data();
        UUID requestId = UUID.fromString(d.get("requestId").toString());
        boolean inserted = repository.insertAssignment(new Assignment(requestId, e.region(),
                UUID.fromString(d.get("clientId").toString()), UUID.fromString(d.get("categoryId").toString()),
                (String) d.get("categoryName"), (String) d.get("addressText"),
                dbl(d.get("latitude")), dbl(d.get("longitude")), Instant.parse(d.get("scheduledAt").toString()),
                "SEARCHING", null, 0, null));
        if (inserted) {
            offerNext(requestId);
        }
    }

    /** Cancelación del pedido: se cierra la búsqueda, se anulan las ofertas y se libera la reserva. */
    @Transactional
    public void onCancelled(CloudEvent e) {
        Map<String, Object> d = e.data();
        UUID requestId = UUID.fromString(d.get("requestId").toString());
        if (bool(d.get("releaseOnly")) && d.get("workerId") != null) {
            repository.release(UUID.fromString(d.get("workerId").toString()), requestId);
            return;
        }
        repository.lockAssignment(requestId).ifPresent(a -> {
            if (!"FAILED".equals(a.status())) repository.updateAssignment(requestId, "CANCELLED", null, a.offersMade());
        });
        repository.cancelPendingOffers(requestId);
        repository.releaseRequest(requestId);
    }

    /** El pedido terminó (completado, cancelado o vencido): el trabajador queda libre. */
    @Transactional
    public void onStatusChanged(CloudEvent e) {
        Object to = e.data().get("to");
        if (to == null || !TERMINAL.contains(to.toString())) return;
        UUID requestId = UUID.fromString(e.data().get("requestId").toString());
        if (repository.releaseRequest(requestId) > 0) {
            repository.assignment(requestId).ifPresent(a ->
                    repository.updateAssignment(requestId, "DONE", null, a.offersMade()));
        }
    }

    // ------------------------------------------------------------------ ofertas

    /** Ofrece el pedido al siguiente candidato. Bloquea la fila del pedido: una sola decisión a la vez. */
    @Transactional
    public void offerNext(UUID requestId) {
        Assignment a = repository.lockAssignment(requestId).orElse(null);
        if (a == null || !("SEARCHING".equals(a.status()) || "OFFERED".equals(a.status()))) return;

        if (a.offersMade() >= properties.maxOffers()) {
            fail(a, "NOBODY_ACCEPTED");
            return;
        }
        for (Matching.Candidate c : matching.candidates(a)) {
            Offer offer = new Offer(UUID.randomUUID(), a.region(), requestId, c.worker().workerId(), "PENDING",
                    c.distanceKm(), Instant.now().plus(properties.offerTimeout()), null);
            if (!repository.insertOffer(offer)) {
                continue;   // el trabajador recibió otra oferta en paralelo: siguiente candidato
            }
            repository.updateAssignment(requestId, "OFFERED", null, a.offersMade() + 1);
            Map<String, Object> data = new HashMap<>();
            data.put("requestId", requestId.toString());
            data.put("offerId", offer.id().toString());
            data.put("workerId", offer.workerId().toString());
            data.put("expiresAt", offer.expiresAt().toString());
            data.put("distanceKm", round(c.distanceKm()));
            data.put("categoryName", a.categoryName());
            data.put("addressText", a.addressText());
            data.put("scheduledAt", a.scheduledAt().toString());
            outbox.publish(EventTypes.DISPATCH_OFFER_CREATED, a.region(), requestId.toString(), requestId.toString(), data);
            log.info("Offered request {} to worker {} ({} km)", requestId, offer.workerId(), round(c.distanceKm()));
            return;
        }
        // Sin candidatos por ahora: se reintenta hasta agotar el plazo de búsqueda
        repository.updateAssignment(requestId, "SEARCHING", null, a.offersMade());
        if (a.createdAt().plus(properties.searchTimeout()).isBefore(Instant.now())) {
            fail(a, a.offersMade() == 0 ? "NO_WORKER_NEARBY" : "NOBODY_ACCEPTED");
        }
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Map<String, Object> accept(UUID workerId, UUID offerId) {
        Offer offer = pendingOfferOf(workerId, offerId);
        if (offer.expiresAt().isBefore(Instant.now())) {
            repository.closeOffer(offerId, "PENDING", "EXPIRED");
            offerNext(offer.requestId());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "OFFER_EXPIRED");
        }
        if (!repository.reserve(workerId, offer.region(), offer.requestId())) {
            repository.closeOffer(offerId, "PENDING", "REJECTED");
            offerNext(offer.requestId());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "WORKER_BUSY");
        }
        repository.closeOffer(offerId, "PENDING", "ACCEPTED");
        Assignment a = repository.lockAssignment(offer.requestId()).orElseThrow();
        repository.updateAssignment(a.requestId(), "ASSIGNED", workerId, a.offersMade());

        String name = repository.worker(a.region(), workerId).map(WorkerRow::name).orElse(null);
        Map<String, Object> data = new HashMap<>();
        data.put("requestId", a.requestId().toString());
        data.put("workerId", workerId.toString());
        data.put("workerName", name);
        data.put("distanceKm", round(offer.distanceKm()));
        outbox.publish(EventTypes.DISPATCH_WORKER_ASSIGNED, a.region(), a.requestId().toString(),
                a.requestId().toString(), data);
        // Tiempo de asignación: desde que el core pidió trabajador hasta la aceptación
        io.micrometer.core.instrument.Metrics.timer("jornal.dispatch.assignment.time", "region", a.region())
                .record(java.time.Duration.between(a.createdAt(), Instant.now()));
        log.info("Worker {} accepted request {}", workerId, a.requestId());
        return Map.of("requestId", a.requestId(), "status", "ASSIGNED");
    }

    @Transactional
    public void reject(UUID workerId, UUID offerId) {
        Offer offer = pendingOfferOf(workerId, offerId);
        repository.closeOffer(offerId, "PENDING", "REJECTED");
        offerNext(offer.requestId());
    }

    /** Ofertas vencidas: se cierran y el pedido pasa al siguiente candidato. */
    @Transactional
    public int expireOffers(Instant now) {
        int expired = 0;
        for (Offer o : repository.expiredOffers(now)) {
            if (repository.closeOffer(o.id(), "PENDING", "EXPIRED")) {
                expired++;
                offerNext(o.requestId());
            }
        }
        return expired;
    }

    /** Búsquedas sin oferta activa: se reintenta (aparecen trabajadores o vence el plazo). */
    @Transactional
    public int retrySearches(Instant olderThan) {
        List<UUID> searching = repository.searchingBefore(olderThan);
        searching.forEach(this::offerNext);
        return searching.size();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> offersOf(UUID workerId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Offer o : repository.pendingOffersOf(workerId)) {
            Assignment a = repository.assignment(o.requestId()).orElse(null);
            if (a == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("offerId", o.id());
            m.put("requestId", o.requestId());
            m.put("expiresAt", o.expiresAt());
            m.put("distanceKm", round(o.distanceKm()));
            m.put("categoryName", a.categoryName());
            m.put("addressText", a.addressText());
            m.put("scheduledAt", a.scheduledAt());
            result.add(m);
        }
        return result;
    }

    // ------------------------------------------------------------------ seguimiento

    /**
     * Posición en vivo: se guarda en Redis y sale por el exchange no durable {@code jornal.live}
     * (sin outbox: gana el último valor y perder uno no importa).
     */
    public LiveLocations.Position updateLocation(String region, UUID workerId, double latitude, double longitude) {
        LiveLocations.Position p = live.update(region, workerId, latitude, longitude);
        Optional<UUID> request = repository.reservedRequestOf(workerId);
        Map<String, Object> data = new HashMap<>();
        data.put("workerId", workerId.toString());
        data.put("requestId", request.map(UUID::toString).orElse(null));
        data.put("latitude", latitude);
        data.put("longitude", longitude);
        data.put("at", p.at().toString());
        CloudEvent event = CloudEvent.of("jornal-dispatch", EventTypes.DISPATCH_WORKER_LOCATION, region,
                workerId.toString(), p.at().toEpochMilli(), data, p.at(), request.map(UUID::toString).orElse(null));
        try {
            rabbit.convertAndSend(Topology.LIVE_EXCHANGE, region + ".worker-location", event);
        } catch (RuntimeException e) {
            log.debug("Live location not published: {}", e.getMessage());
        }
        return p;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> tracking(UUID viewerId, UUID requestId) {
        Assignment a = repository.assignment(requestId)
                .filter(x -> viewerId.equals(x.clientId()) || viewerId.equals(x.workerId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("requestId", requestId);
        m.put("status", a.status());
        m.put("workerId", a.workerId());
        Optional<LiveLocations.Position> p = a.workerId() == null ? Optional.empty() : live.position(a.region(), a.workerId());
        m.put("position", p.orElse(null));
        m.put("distanceKm", p.map(x -> round(LiveLocations.distanceKm(x.latitude(), x.longitude(),
                a.latitude(), a.longitude()))).orElse(null));
        return m;
    }

    // ------------------------------------------------------------------ internos

    private Offer pendingOfferOf(UUID workerId, UUID offerId) {
        Offer offer = repository.lockOffer(offerId)
                .filter(o -> o.workerId().equals(workerId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND"));
        if (!"PENDING".equals(offer.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "OFFER_NOT_PENDING");
        }
        return offer;
    }

    private void fail(Assignment a, String reason) {
        repository.updateAssignment(a.requestId(), "FAILED", null, a.offersMade());
        outbox.publish(EventTypes.DISPATCH_ASSIGNMENT_FAILED, a.region(), a.requestId().toString(),
                a.requestId().toString(), Map.of("requestId", a.requestId().toString(), "reason", reason));
        io.micrometer.core.instrument.Metrics.counter("jornal.dispatch.assignment.failed", "region", a.region(),
                "reason", reason).increment();
        log.info("Assignment failed for request {}: {}", a.requestId(), reason);
    }

    private static double round(double km) {
        return Math.round(km * 10) / 10.0;
    }

    private static boolean bool(Object v) {
        return v instanceof Boolean b ? b : v != null && Boolean.parseBoolean(v.toString());
    }

    private static Double dbl(Object v) {
        return v == null ? null : ((Number) v).doubleValue();
    }
}
