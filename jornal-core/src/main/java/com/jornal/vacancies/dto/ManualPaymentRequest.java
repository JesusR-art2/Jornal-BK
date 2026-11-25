package com.jornal.vacancies.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Pago a mano registrado por el empleador.
 *
 * @param amountMinor   lo que pagó, en unidad menor
 * @param justification obligatoria si {@code amountMinor} es menor a lo que dice la vacante
 */
public record ManualPaymentRequest(@NotNull @PositiveOrZero Long amountMinor,
                                   @Size(max = 1000) String justification) {
}
