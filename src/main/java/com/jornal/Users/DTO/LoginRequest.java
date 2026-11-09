package com.jornal.Users.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.Locale;

public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
    public LoginRequest{
        email = email == null ? null : email.trim().toLowerCase();
    }
}
