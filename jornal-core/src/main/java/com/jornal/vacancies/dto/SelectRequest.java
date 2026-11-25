package com.jornal.vacancies.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/** Postulaciones que el empleador contrata (varias a la vez, hasta completar los cupos). */
public record SelectRequest(@NotEmpty @Size(max = 200) Set<UUID> applicationIds) {
}
