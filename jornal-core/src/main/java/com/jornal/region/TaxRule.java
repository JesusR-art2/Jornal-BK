package com.jornal.region;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.LocalDate;

/** Impuesto de un país. {@code rateBps}: puntos básicos (1900 = 19 %). */
@Embeddable
public record TaxRule(
        @Column(name = "name", nullable = false) String name,
        @Column(name = "rate_bps", nullable = false) int rateBps,
        @Column(name = "included", nullable = false) boolean included,
        @Column(name = "source", nullable = false) String source,
        @Column(name = "valid_from", nullable = false) LocalDate validFrom,
        @Enumerated(EnumType.STRING)
        @Column(name = "review_status", nullable = false) ReviewStatus reviewStatus) {
}
