package com.jornal.vacancies.dto;

import jakarta.validation.constraints.Size;

/** Postulación del trabajador; el mensaje es opcional. */
public record ApplyRequest(@Size(max = 500) String message) {
}
