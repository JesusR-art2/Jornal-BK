package com.jornal.users.dto;

import com.jornal.users.enums.VerificationState;
import jakarta.validation.constraints.NotNull;

public record VerificationRequest(@NotNull VerificationState state) {
}
