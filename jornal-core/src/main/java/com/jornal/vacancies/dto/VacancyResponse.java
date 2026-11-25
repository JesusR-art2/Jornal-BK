package com.jornal.vacancies.dto;

import com.jornal.vacancies.ApplicationStatus;
import com.jornal.vacancies.VacancyStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Vacante con todo lo que ve el trabajador: lugar, implementos, día y hora, y qué hay que hacer.
 *
 * @param filledPositions personas elegidas que ocupan cupo (por confirmar, confirmadas o finalizadas)
 * @param distanceKm      distancia desde la ubicación base del trabajador que consulta (si la tiene)
 * @param myApplication   estado de la postulación del trabajador que consulta, o null
 * @param applicants      número de postulaciones (solo lo ve el empleador)
 */
public record VacancyResponse(
        UUID id,
        UUID employerId,
        String employerName,
        String title,
        String description,
        UUID categoryId,
        List<String> items,
        String addressText,
        double latitude,
        double longitude,
        String timezone,
        Instant startsAt,
        Instant endsAt,
        int positions,
        long filledPositions,
        long payMinor,
        String currency,
        int moneyScale,
        VacancyStatus status,
        Double distanceKm,
        ApplicationStatus myApplication,
        Long applicants,
        Instant createdAt,
        /** Idioma en que se escribió (es, en, pt...). */
        String sourceLanguage,
        /** El título, la descripción y los implementos se tradujeron automáticamente al idioma de quien consulta. */
        boolean translated) {
}
