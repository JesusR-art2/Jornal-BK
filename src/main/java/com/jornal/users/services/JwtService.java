package com.jornal.users.services;

import com.jornal.shared.JwtProperties;
import com.jornal.users.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;
    private final String region;

    public JwtService(JwtEncoder encoder, JwtProperties properties, Clock clock,
                      @Value("${jornal.region.code}") String region) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
        this.region = region;
    }

    public record AccessToken(String value, long expiresIn) {
    }

    /** JWT de acceso. Lleva la región que lo emitió: otra región lo rechaza. */
    public AccessToken issue(User user) {
        Instant now = clock.instant();
        long minutes = properties.expirationMinutes();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(minutes, ChronoUnit.MINUTES))
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("name", user.getName())
                .claim("role", user.getRol().name())
                .claim("region", region)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(token, minutes * 60);
    }
}
