package com.jornal.users.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Campos nulos no se tocan. Un teléfono vacío lo borra. */
public record UpdateUserRequest(
        @Size(min = 1, max = 80) String givenName,
        @Size(min = 1, max = 80) String familyName,
        @Size(max = 30) String phone,
        @Pattern(regexp = "[a-z]{2}-[A-Z]{2}") String locale
) {
    public UpdateUserRequest {
        givenName = givenName == null ? null : givenName.trim();
        familyName = familyName == null ? null : familyName.trim();
        phone = phone == null ? null : phone.trim();
    }
}
