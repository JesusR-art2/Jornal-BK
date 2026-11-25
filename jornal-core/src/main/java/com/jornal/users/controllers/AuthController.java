package com.jornal.users.controllers;

import com.jornal.users.dto.LoginRequest;
import com.jornal.users.dto.RefreshRequest;
import com.jornal.users.dto.RegisterRequest;
import com.jornal.users.dto.TokenResponse;
import com.jornal.users.dto.UserResponse;
import com.jornal.users.services.AuthService;
import com.jornal.users.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return userService.save(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        // Detrás del gateway, getRemoteAddr() ya es la IP del cliente (X-Forwarded-For con forward-headers-strategy)
        return authService.login(request, http.getRemoteAddr());
    }

    /** Entrega un access token nuevo y rota el refresh token. */
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    /** Cierra la sesión revocando el refresh token y su familia. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }
}
