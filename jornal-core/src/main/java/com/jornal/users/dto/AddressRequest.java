package com.jornal.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Dirección con los campos del país (ver {@code GET /region-profile}, atributo {@code addressSchema}).
 * Ejemplo CO: {@code {"street_address": "Calle 10 # 5-20", "city": "Bogotá", "department": "Cundinamarca"}}.
 */
public record AddressRequest(
        @Size(max = 40) String label,
        @NotEmpty Map<@NotBlank @Size(max = 40) String, @Size(max = 200) String> fields) {
}
