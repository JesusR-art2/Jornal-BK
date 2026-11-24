package com.jornal.workers.listeners;

import com.jornal.requests.WorkerRated;
import com.jornal.workers.WorkerProfileRepository;
import com.jornal.workers.WorkerSnapshots;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Actualiza el promedio del trabajador con cada calificación del cliente y publica su foto (asignación ordena por ella). */
@Component
@RequiredArgsConstructor
public class WorkerRatingListener {

    private final WorkerProfileRepository repository;
    private final WorkerSnapshots snapshots;

    @EventListener
    @Transactional
    public void onWorkerRated(WorkerRated event) {
        repository.findById(event.workerId()).ifPresent(profile -> {
            int count = profile.getRatingCount();
            BigDecimal total = profile.getRatingAvg().multiply(BigDecimal.valueOf(count))
                    .add(BigDecimal.valueOf(event.score()));
            profile.setRatingCount(count + 1);
            profile.setRatingAvg(total.divide(BigDecimal.valueOf(count + 1L), 2, RoundingMode.HALF_UP));
            snapshots.publish(profile);
        });
    }
}
