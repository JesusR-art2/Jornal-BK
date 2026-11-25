package com.jornal.vacancies;

/** Motivo de cancelar a un trabajador después del trabajo (no se le paga). */
public enum CloseReason {
    /** No se presentó. */
    NO_SHOW,
    /** Dejó el trabajo a medias. */
    ABANDONED,
    OTHER
}
