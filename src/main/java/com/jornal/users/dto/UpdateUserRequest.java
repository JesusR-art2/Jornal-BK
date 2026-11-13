package com.jornal.users.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(min = 1, max = 120) String name,
        @Pattern(regexp = "^\\+?[0-9]{7,15}", message = "Invalid phone number") String phone
) {
    public UpdateUserRequest{
        name = name == null ? null : name.trim();
        phone = phone == null ? null : phone.trim();
    }
}
