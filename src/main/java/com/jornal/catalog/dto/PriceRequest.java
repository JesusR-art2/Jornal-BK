package com.jornal.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

/** Precio en la región de esta instancia; la moneda sale del perfil regional. */
public record PriceRequest(
        @NotBlank @Pattern(regexp = "DAY|HOUR|TRIP|UNIT") String unit,
        @PositiveOrZero long amountMinor) {
}
