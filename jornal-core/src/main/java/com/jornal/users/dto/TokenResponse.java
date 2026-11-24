package com.jornal.users.dto;

/**
 * {@code accessToken}: JWT corto (15 min). {@code refreshToken}: token opaco para {@code POST /auth/refresh};
 * cada uso devuelve uno nuevo y el anterior deja de servir.
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn,
                            String refreshToken, long refreshExpiresIn) {
}
