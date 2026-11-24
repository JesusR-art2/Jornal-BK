package com.jornal.users.dto;

import com.jornal.users.Rol;
import com.jornal.users.enums.VerificationState;

import java.util.UUID;

/** {@code name} es el nombre para mostrar, ya ordenado según el país. */
public record UserResponse(UUID id, String email, String name, String givenName, String familyName,
                           Rol rol, VerificationState verificationState, String phone,
                           String countryCode,
                           String locale,
                           boolean consentUpToDate) {

    public UserResponse withConsentUpToDate(boolean value) {
        return new UserResponse(id, email, name, givenName, familyName, rol, verificationState,
                phone, countryCode, locale, value);
    }
}
