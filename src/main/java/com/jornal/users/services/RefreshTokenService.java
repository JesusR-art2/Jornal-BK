package com.jornal.users.services;

import com.jornal.shared.ApiException;
import com.jornal.users.RefreshProperties;
import com.jornal.users.RefreshToken;
import com.jornal.users.RefreshTokenRepository;
import com.jornal.users.enums.ErrorUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Refresh tokens opacos con rotación: cada uso entrega uno nuevo y revoca el anterior.
 * Si llega un token ya rotado (posible robo), se revoca toda su familia.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final RefreshProperties properties;
    private final Clock clock;

    public record Issued(String token, long expiresInSeconds) {
    }

    public record Rotation(UUID userId, Issued next) {
    }

    @Transactional
    public Issued issue(UUID userId) {
        return create(userId, UUID.randomUUID()).issued();
    }

    /**
     * Valida y rota el token. Las revocaciones por reuso se guardan aunque la llamada termine en error.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Rotation rotate(String rawToken) {
        RefreshToken current = find(rawToken);
        Instant now = clock.instant();
        if (current.isRevoked()) {
            if (current.getReplacedBy() != null) {
                log.warn("Refresh token reuse detected for user {}; revoking family {}",
                        current.getUserId(), current.getFamilyId());
                repository.revokeFamily(current.getFamilyId(), now);
            }
            throw invalid();
        }
        if (!current.getExpiresAt().isAfter(now)) {
            throw invalid();
        }
        Created next = create(current.getUserId(), current.getFamilyId());
        current.setRevokedAt(now);
        current.setReplacedBy(next.entity().getId());
        return new Rotation(current.getUserId(), next.issued());
    }

    /** Cierra la sesión: revoca la familia del token. No revela si el token existía. */
    @Transactional
    public void revokeFamily(String rawToken) {
        repository.findByTokenHash(hash(rawToken))
                .ifPresent(t -> repository.revokeFamily(t.getFamilyId(), clock.instant()));
    }

    /** Cierra todas las sesiones del usuario (cambio de contraseña, bloqueo o borrado). */
    @Transactional
    public void revokeAllFor(UUID userId) {
        repository.revokeAllOfUser(userId, clock.instant());
    }

    private record Created(RefreshToken entity, Issued issued) {
    }

    private Created create(UUID userId, UUID familyId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();

        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setUserId(userId);
        token.setFamilyId(familyId);
        token.setTokenHash(hash(raw));
        token.setIssuedAt(now);
        token.setExpiresAt(now.plus(properties.ttl()));
        repository.save(token);
        return new Created(token, new Issued(raw, properties.ttl().toSeconds()));
    }

    private RefreshToken find(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw invalid();
        return repository.findByTokenHash(hash(rawToken)).orElseThrow(RefreshTokenService::invalid);
    }

    private static ApiException invalid() {
        return new ApiException(ErrorUser.AUTH001_INVALID_REFRESH_TOKEN);
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
