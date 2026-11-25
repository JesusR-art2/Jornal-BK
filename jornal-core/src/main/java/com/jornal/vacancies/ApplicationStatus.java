package com.jornal.vacancies;

/**
 * Ciclo de una postulación:
 * <pre>
 * APPLIED ──(empleador elige)──▶ SELECTED ──(trabajador: sí asisto)──▶ CONFIRMED ──▶ COMPLETED (pasa a pago)
 *    │                              │                                      └──▶ CANCELLED (no fue o dejó el trabajo; sin pago)
 *    │                              └──(trabajador: no puedo)──▶ DECLINED (el cupo se libera)
 *    ├──(trabajador retira)──▶ WITHDRAWN
 *    └──(vacante llena, cerrada o cancelada)──▶ NOT_SELECTED
 * </pre>
 */
public enum ApplicationStatus {
    APPLIED,
    SELECTED,
    CONFIRMED,
    DECLINED,
    WITHDRAWN,
    NOT_SELECTED,
    COMPLETED,
    CANCELLED;

    /** Ocupa un cupo de la vacante. */
    public boolean holdsSlot() {
        return this == SELECTED || this == CONFIRMED || this == COMPLETED;
    }
}
