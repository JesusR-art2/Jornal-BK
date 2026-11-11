package com.jornal.Users.DTO;
import com.jornal.Users.Rol;
import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 30) String phone,
        @NotNull Rol rol

) {
    public RegisterRequest{
        email = email == null ? null : email.trim().toLowerCase();
        name = name == null ? null : name.trim();
    }
}
