package com.jornal.workers;

import java.util.UUID;

/** Trabajador cuyo radio de trabajo alcanza un punto, con la distancia desde su ubicación base. */
public record NearbyWorker(UUID workerId, double distanceKm) {
}
