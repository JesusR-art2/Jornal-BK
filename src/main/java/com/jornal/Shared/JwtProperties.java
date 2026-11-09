package com.jornal.Shared;

import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = "jornal.security.jwt")
public record JwtProperties(String secret, String issuer, long expirationMinutes) {
}
