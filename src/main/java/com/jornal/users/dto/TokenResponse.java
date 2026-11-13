package com.jornal.users.dto;

public record TokenResponse(String accesToken, String tokenType, long expiresIn) {
}
