package com.jornal.users.dto;

import com.jornal.users.Rol;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/**
 * Registro en la región de esta instancia. {@code country} es opcional: si viene, debe coincidir con la región.
 * {@code business} es obligatorio para el rol BUSINESS y no se permite en los demás.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 80) String givenName,
        @NotBlank @Size(max = 80) String familyName,
        @Size(max = 30) String phone,
        @NotNull Rol rol,
        @Pattern(regexp = "[A-Za-z]{2}") String country,
        @Pattern(regexp = "[a-z]{2}-[A-Z]{2}") String locale,
        @AssertTrue(message = "TERMS_NOT_ACCEPTED") boolean acceptedTerms,
        @Valid AddressRequest address,
        @Valid BusinessRequest business
) {
    public RegisterRequest {
        email = email == null ? null : email.trim().toLowerCase();
        givenName = givenName == null ? null : givenName.trim();
        familyName = familyName == null ? null : familyName.trim();
    }
}
