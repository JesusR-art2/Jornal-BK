package com.jornal.workers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkerApi {
    Optional<WorkerSummary> findSummaryById(UUID workerId);

    /** Trabajadores disponibles ahora en esta región (tablero de administración). */
    long countAvailable();

    /** Trabajadores cuyo radio de trabajo (desde su ubicación base) alcanza el punto, el más cercano primero. */
    List<NearbyWorker> findNearby(double latitude, double longitude, int limit);

    /** Distancia en km desde la ubicación base del trabajador; vacío si no la tiene. */
    Optional<Double> distanceKm(UUID workerId, double latitude, double longitude);
}
