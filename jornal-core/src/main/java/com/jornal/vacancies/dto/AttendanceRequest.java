package com.jornal.vacancies.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Respuesta del trabajador al botón «¿Puedes asistir?»: {@code true} confirma, {@code false} libera el cupo. */
public record AttendanceRequest(@NotNull Boolean attending, @Size(max = 500) String comment) {
}
