package com.jornal.region;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Campo de dirección de un país, en el orden en que se muestra.
 * {@code regex} es opcional; para {@code postal_code} sin regex se usa la del perfil.
 */
@Embeddable
public record AddressField(
        @Column(name = "field_key", nullable = false) String fieldKey,
        @Column(name = "label_key", nullable = false) String labelKey,
        @Column(name = "required", nullable = false) boolean required,
        @Column(name = "regex") String regex,
        @Column(name = "max_length", nullable = false) int maxLength) {
}
