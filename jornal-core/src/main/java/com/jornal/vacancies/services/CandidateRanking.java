package com.jornal.vacancies.services;

import java.math.BigDecimal;

/**
 * Puntaje (0 a 100) para sugerir al empleador quiénes podrían ser mejores para el trabajo:
 * 50 % calificación promedio, 30 % trabajos de vacantes terminados (hasta 10), 15 % cercanía
 * y 5 % identidad verificada. Sin calificaciones cuenta como 3 de 5; sin ubicación, como 10 km.
 */
public final class CandidateRanking {

    private CandidateRanking() {
    }

    public static double score(BigDecimal ratingAvg, int ratingCount, long completedJobs, Double distanceKm,
                               boolean verified) {
        double rating = ratingCount == 0 || ratingAvg == null ? 3.0 : ratingAvg.doubleValue();
        double ratingPart = rating / 5.0 * 50.0;
        double experiencePart = Math.min(completedJobs, 10) / 10.0 * 30.0;
        double km = distanceKm == null ? 10.0 : distanceKm;
        double distancePart = Math.max(0.0, 1.0 - km / 30.0) * 15.0;
        double verifiedPart = verified ? 5.0 : 0.0;
        return Math.round((ratingPart + experiencePart + distancePart + verifiedPart) * 10.0) / 10.0;
    }
}
