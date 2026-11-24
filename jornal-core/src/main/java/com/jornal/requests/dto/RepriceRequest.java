package com.jornal.requests.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

/** Cambios antes de la asignación. Los campos nulos se conservan. Recotiza y vuelve a autorizar el pago. */
public record RepriceRequest(
        @Min(1) @Max(30) Integer units,
        LocalDateTime scheduledLocal,
        @Size(max = 40) String timezone,
        @PositiveOrZero Long tipMinor,
        @Min(0) @Max(100) Integer tipPercent,
        @Size(max = 500) String notes) {
}
