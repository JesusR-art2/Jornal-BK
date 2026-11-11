package com.jornal.Users.DTO;

import com.jornal.Users.enums.VerificationState;
import jakarta.validation.constraints.NotNull;

public record VerificationRequest(@NotNull VerificationState state) {
}
