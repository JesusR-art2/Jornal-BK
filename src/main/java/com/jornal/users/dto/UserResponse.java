package com.jornal.users.dto;

import com.jornal.users.Rol;
import com.jornal.users.enums.VerificationState;

import java.util.UUID;

public record UserResponse(UUID id, String email, String name, Rol rol, VerificationState verificationState, String phone,
                           String countryCode,
                           String locale,
                           boolean consentUpToDate) {
    public UserResponse withConsentUpToDate(boolean value) {
        return new UserResponse(id, email, name, rol, verificationState,
                phone, countryCode, locale, value);
    }

}
