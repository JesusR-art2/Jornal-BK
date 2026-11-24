package com.jornal.requests.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Carrito: lo que el cliente quiere pedir. Se usa para cotizar ({@code POST /requests/quote}) y para crear.
 *
 * @param scheduledLocal fecha y hora locales del trabajo, en {@code timezone} (si falta, la zona por defecto del país)
 * @param address        campos de la dirección según el esquema del país
 * @param tipMinor       propina en unidad menor; o {@code tipPercent} sobre el subtotal
 */
public record RequestDraft(
        @NotNull UUID categoryId,
        @NotNull @Min(1) @Max(30) Integer units,
        @NotNull LocalDateTime scheduledLocal,
        @Size(max = 40) String timezone,
        @NotEmpty Map<@NotBlank String, @Size(max = 200) String> address,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @NotBlank @Size(max = 30) String paymentMethod,
        @PositiveOrZero Long tipMinor,
        @Min(0) @Max(100) Integer tipPercent,
        @Size(max = 500) String notes) {
}
