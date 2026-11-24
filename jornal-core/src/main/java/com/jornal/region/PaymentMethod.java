package com.jornal.region;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** Medio de pago disponible en un país; {@code flow} elige la estrategia de pago. */
@Embeddable
public record PaymentMethod(
        @Column(name = "code", nullable = false) String code,
        @Column(name = "label_key", nullable = false) String labelKey,
        @Enumerated(EnumType.STRING)
        @Column(name = "flow", nullable = false) PaymentFlow flow) {
}
