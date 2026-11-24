package com.jornal.users.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Datos del comercio. {@code taxIdType} debe ser un documento de clase TAX del país
 * (por ejemplo NIT, CNPJ, EIN, SIRET o CORPORATE_NUMBER) y se valida con su estrategia.
 */
public record BusinessRequest(
        @NotBlank @Size(max = 160) String legalName,
        @NotBlank @Size(max = 30) String taxIdType,
        @NotBlank @Size(max = 30) String taxIdNumber,
        @NotNull @Valid AddressRequest billingAddress) {

    public BusinessRequest {
        legalName = legalName == null ? null : legalName.trim();
        taxIdType = taxIdType == null ? null : taxIdType.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
