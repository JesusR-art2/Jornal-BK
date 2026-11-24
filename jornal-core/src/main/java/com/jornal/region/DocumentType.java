package com.jornal.region;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * Tipo de documento aceptado en un país. {@code validator} es el nombre de la estrategia
 * ({@code regex}, {@code nit-dv}, {@code cpf}, {@code cnpj}, {@code luhn}, {@code jp-mynumber}, {@code jp-corporate}).
 */
@Embeddable
public record DocumentType(
        @Column(name = "code", nullable = false) String code,
        @Enumerated(EnumType.STRING)
        @Column(name = "kind", nullable = false) DocumentKind kind,
        @Column(name = "validator", nullable = false) String validator,
        @Column(name = "regex") String regex) {
}
