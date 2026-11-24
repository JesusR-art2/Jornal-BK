package com.jornal.workers.dto;

import jakarta.validation.constraints.*;

import java.util.Set;
import java.util.UUID;

/**
 * Perfil del trabajador. {@code baseLatitude}/{@code baseLongitude}: desde dónde sale a trabajar; se usa para
 * buscar candidatos cuando no hay posición en vivo. Es obligatoria para quedar disponible.
 */
public record WorkerProfileRequest(
        @Size(max = 750) String bio,
        @NotNull @Min(1) @Max(100) Integer workRadiusKm,
        @NotEmpty @Size(max = 5) Set<UUID> categoryIds,
        @DecimalMin("-90") @DecimalMax("90") Double baseLatitude,
        @DecimalMin("-180") @DecimalMax("180") Double baseLongitude) {

    public WorkerProfileRequest {
        bio = bio == null || bio.isBlank() ? null : bio.trim();
    }
}
