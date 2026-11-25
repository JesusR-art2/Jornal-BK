package com.jornal.vacancies.dto;

import jakarta.validation.constraints.NotNull;

/** Configuración del empleador: {@code autoPayout} activa el pago automático al finalizar. */
public record EmployerSettingsDto(@NotNull Boolean autoPayout) {
}
