package com.jornal.Users.DTO;

public record TokenResponse(String accesToken, String tokenType, long expiresIn) {
}
