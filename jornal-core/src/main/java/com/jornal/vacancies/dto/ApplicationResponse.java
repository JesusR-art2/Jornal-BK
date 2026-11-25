package com.jornal.vacancies.dto;

import com.jornal.vacancies.ApplicationStatus;
import com.jornal.vacancies.CloseReason;
import com.jornal.vacancies.PayoutMode;
import com.jornal.vacancies.PayoutStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Postulación vista por el empleador (candidatos, cierre y formato de pago) o por el trabajador.
 *
 * @param score          puntaje para ordenar candidatos (más alto = mejor opción); ver {@code CandidateRanking}
 * @param completedJobs  trabajos de vacantes que el trabajador terminó y le finalizaron
 */
public record ApplicationResponse(
        UUID id,
        UUID vacancyId,
        String vacancyTitle,
        UUID workerId,
        String workerName,
        boolean workerVerified,
        BigDecimal ratingAvg,
        int ratingCount,
        long completedJobs,
        Double distanceKm,
        double score,
        ApplicationStatus status,
        String message,
        Instant selectedAt,
        Instant respondedAt,
        Instant closedAt,
        CloseReason closeReason,
        String justification,
        Long payoutMinor,
        String currency,
        int moneyScale,
        PayoutStatus payoutStatus,
        PayoutMode payoutMode,
        Long paidAmountMinor,
        String paymentNote,
        String payoutRef,
        String payoutFailure,
        Instant paidAt,
        Instant startsAt,
        Instant endsAt,
        Instant createdAt) {
}
