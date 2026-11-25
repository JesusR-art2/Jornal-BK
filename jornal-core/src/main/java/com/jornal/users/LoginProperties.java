package com.jornal.users;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Límite de intentos de inicio de sesión fallidos (contados en Redis).
 *
 * @param maxAttempts      fallos por cuenta dentro de la ventana antes de bloquearla
 * @param maxAttemptsPerIp fallos por dirección IP dentro de la ventana antes de bloquearla
 * @param window           duración de la ventana; empieza con el primer fallo
 */
@ConfigurationProperties("jornal.security.login")
public record LoginProperties(int maxAttempts, int maxAttemptsPerIp, Duration window) {
    public LoginProperties {
        if (maxAttempts <= 0) maxAttempts = 5;
        if (maxAttemptsPerIp <= 0) maxAttemptsPerIp = 20;
        if (window == null) window = Duration.ofMinutes(15);
    }
}
