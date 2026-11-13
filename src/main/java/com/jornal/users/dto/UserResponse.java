package com.jornal.users.dto;

import com.jornal.users.Rol;
import com.jornal.users.enums.VerificationState;

import java.util.UUID;

public record UserResponse(UUID id, String email, String name, Rol rol, VerificationState verificationState) {

}
