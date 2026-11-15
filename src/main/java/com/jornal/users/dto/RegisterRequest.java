package com.jornal.users.dto;
import com.jornal.users.Rol;
import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 30) String phone,
        @NotNull Rol rol,
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String country,
        @Pattern(regexp = "[a-z]{2}-[A-Z]{2}") String locale,
        @AssertTrue(message = "TERMS_NOT_ACCEPTED") boolean acceptedTerms

) {
    public RegisterRequest{
        email = email == null ? null : email.trim().toLowerCase();
        name = name == null ? null : name.trim();
    }
}
