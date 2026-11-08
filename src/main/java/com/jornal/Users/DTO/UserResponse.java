package com.jornal.Users.DTO;

import com.jornal.Users.User;
import com.jornal.Users.enums.Rol;
import com.jornal.Users.enums.VerificationState;

import java.util.UUID;

public record UserResponse(UUID id, String email, String name, Rol rol, VerificationState verificationState) {

}
