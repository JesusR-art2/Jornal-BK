package com.jornal.users.services;

import com.jornal.shared.ApiException;
import com.jornal.users.User;
import com.jornal.users.UserRepository;
import com.jornal.users.dto.LoginRequest;
import com.jornal.users.dto.TokenResponse;
import com.jornal.users.enums.ErrorUser;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final LoginAttempts loginAttempts;

    private String dummyHash;

    @PostConstruct
    void init() {
        dummyHash = passwordEncoder.encode("jornal-dummy-password");
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        return login(request, null);
    }

    /**
     * Inicio de sesión con límite de intentos fallidos por cuenta y por IP ({@code AUTH002_TOO_MANY_ATTEMPTS}).
     * Con la cuenta bloqueada se responde 429 aunque la clave sea correcta, hasta que vence la ventana.
     */
    @Transactional
    public TokenResponse login(LoginRequest request, String clientIp) {
        loginAttempts.check(request.email(), clientIp);
        User user = repository.findByEmail(request.email()).orElse(null);

        // Se compara siempre con un hash, exista o no el usuario, para que el tiempo de respuesta no revele emails.
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean passwordOk = passwordEncoder.matches(request.password(), hash);

        if (user == null || !passwordOk || !user.isActive()) {
            loginAttempts.failed(request.email(), clientIp);
            throw new ApiException(ErrorUser.USER003_INVALID_CREDENTIALS);
        }
        loginAttempts.succeeded(request.email());
        return tokens(user, refreshTokens.issue(user.getId()));
    }

    /** Rota el refresh token y emite un access token nuevo. Una cuenta bloqueada o borrada no renueva. */
    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse refresh(String refreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokens.rotate(refreshToken);
        User user = repository.findById(rotation.userId())
                .filter(u -> u.isActive() && u.getDeletedAt() == null)
                .orElse(null);
        if (user == null) {
            refreshTokens.revokeAllFor(rotation.userId());
            throw new ApiException(ErrorUser.AUTH001_INVALID_REFRESH_TOKEN);
        }
        return tokens(user, rotation.next());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.revokeFamily(refreshToken);
    }

    private TokenResponse tokens(User user, RefreshTokenService.Issued refresh) {
        JwtService.AccessToken access = jwtService.issue(user);
        return new TokenResponse(access.value(), "Bearer", access.expiresIn(),
                refresh.token(), refresh.expiresInSeconds());
    }
}
