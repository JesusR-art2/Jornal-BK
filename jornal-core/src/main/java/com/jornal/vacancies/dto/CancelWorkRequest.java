package com.jornal.vacancies.dto;

import com.jornal.vacancies.CloseReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** El empleador cancela a una persona tras el trabajo (no fue o lo dejó a medias): no se le paga. */
public record CancelWorkRequest(@NotNull CloseReason reason, @NotBlank @Size(max = 1000) String justification) {
}
