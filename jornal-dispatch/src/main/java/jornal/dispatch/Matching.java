package jornal.dispatch;

import jornal.dispatch.DispatchRepository.Assignment;
import jornal.dispatch.DispatchRepository.WorkerRow;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Candidatos para un pedido: misma categoría, elegibles y disponibles, dentro de su radio, sin reserva, sin otra
 * oferta pendiente y a los que no se les ofreció ya este pedido. Posición: la en vivo si está vigente; si no,
 * la base del perfil.
 *
 * <p>Orden (proximidad con balanceo de carga): distancia redondeada al kilómetro, luego menos pedidos asignados
 * hoy, luego mejor calificación.
 */
@Component
public class Matching {

    public record Candidate(WorkerRow worker, double distanceKm, int jobsToday) {
    }

    private static final Comparator<Candidate> ORDER = Comparator
            .comparingLong((Candidate c) -> Math.round(c.distanceKm()))
            .thenComparingInt(Candidate::jobsToday)
            .thenComparing(Comparator.comparingDouble((Candidate c) -> c.worker().ratingAvg()).reversed());

    private final DispatchRepository repository;
    private final LiveLocations live;

    public Matching(DispatchRepository repository, LiveLocations live) {
        this.repository = repository;
        this.live = live;
    }

    public List<Candidate> candidates(Assignment a) {
        Set<UUID> excluded = new HashSet<>(repository.reservedWorkers());
        excluded.addAll(repository.workersWithPendingOffer());
        excluded.addAll(repository.workersOfferedFor(a.requestId()));
        Map<UUID, Integer> jobsToday = repository.assignedSince(a.region(),
                Instant.now().atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).toInstant());

        List<Candidate> result = new ArrayList<>();
        for (WorkerRow w : repository.availableWorkers(a.region())) {
            if (excluded.contains(w.workerId()) || !w.categoryIds().contains(a.categoryId())) continue;
            double[] position = live.position(a.region(), w.workerId())
                    .map(p -> new double[]{p.latitude(), p.longitude()})
                    .orElseGet(() -> w.baseLatitude() == null || w.baseLongitude() == null ? null
                            : new double[]{w.baseLatitude(), w.baseLongitude()});
            if (position == null) continue;
            double km = LiveLocations.distanceKm(position[0], position[1], a.latitude(), a.longitude());
            if (km > w.radiusKm()) continue;
            result.add(new Candidate(w, km, jobsToday.getOrDefault(w.workerId(), 0)));
        }
        result.sort(ORDER);
        return result;
    }
}
