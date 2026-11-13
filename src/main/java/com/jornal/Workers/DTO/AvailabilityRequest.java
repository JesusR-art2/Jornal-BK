package com.jornal.Workers.DTO;

import jakarta.validation.constraints.NotNull;

public record AvailabilityRequest(@NotNull Boolean available) {
}
