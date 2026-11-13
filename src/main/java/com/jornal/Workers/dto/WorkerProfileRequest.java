package com.jornal.Workers.dto;

import jakarta.validation.constraints.*;

import java.util.Set;
import java.util.UUID;

public record WorkerProfileRequest(
        @Size(max = 750) String bio,
        @NotNull @Min(1) @Max(100) Integer workRadiusKm,
        @NotEmpty @Size(max = 5)Set<UUID> categorysIds
        ) {
    public WorkerProfileRequest{
        bio = bio == null || bio.isBlank() ? null : bio.trim();
    }
}
