package com.jornal.vacancies;

/**
 * OPEN: recibe postulaciones. FILLED: ya tiene a todas las personas que necesita (confirmadas o por confirmar).
 * CLOSED: el empleador cerró a todas las personas tras el trabajo. CANCELLED: el empleador la retiró.
 */
public enum VacancyStatus {
    OPEN,
    FILLED,
    CLOSED,
    CANCELLED
}
