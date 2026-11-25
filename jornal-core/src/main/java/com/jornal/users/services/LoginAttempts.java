package com.jornal.users.services;

import com.jornal.region.RegionApi;
import com.jornal.shared.ApiException;
import com.jornal.users.enums.ErrorUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.jornal.users.LoginProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Límite de intentos de inicio de sesión fallidos, contado en Redis (compartido por todas las instancias).
 * <ul>
 *   <li>Por cuenta: {@code maxAttempts} fallos dentro de {@code window} bloquean esa cuenta hasta que vence la
 *       ventana, aunque la clave siguiente sea correcta (protege contra adivinar claves).</li>
 *   <li>Por dirección IP: {@code maxAttemptsPerIp} fallos bloquean esa IP (protege contra probar muchas cuentas).</li>
 * </ul>
 * Un inicio de sesión correcto borra el contador de la cuenta. Si Redis no responde, no se bloquea a nadie
 * (falla abierta): el límite es una protección extra y no debe impedir entrar.
 */
@Component
public class LoginAttempts {

    private static final Logger log = LoggerFactory.getLogger(LoginAttempts.class);

    private final StringRedisTemplate redis;
    private final LoginProperties properties;
    private final String region;

    public LoginAttempts(StringRedisTemplate redis, LoginProperties properties, RegionApi regions) {
        this.redis = redis;
        this.properties = properties;
        this.region = regions.currentCode().toLowerCase(Locale.ROOT);
    }

    /** Lanza {@code AUTH002_TOO_MANY_ATTEMPTS} si la cuenta o la IP agotaron sus intentos. */
    public void check(String email, String ip) {
        if (count(accountKey(email)) >= properties.maxAttempts()
                || (ip != null && count(ipKey(ip)) >= properties.maxAttemptsPerIp())) {
            throw new ApiException(ErrorUser.AUTH002_TOO_MANY_ATTEMPTS);
        }
    }

    public void failed(String email, String ip) {
        increment(accountKey(email));
        if (ip != null) increment(ipKey(ip));
    }

    public void succeeded(String email) {
        try {
            redis.delete(accountKey(email));
        } catch (RuntimeException e) {
            log.warn("Login attempts not reset (Redis unavailable): {}", e.getMessage());
        }
    }

    private long count(String key) {
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? 0 : Long.parseLong(value);
        } catch (RuntimeException e) {
            log.warn("Login attempts not checked (Redis unavailable): {}", e.getMessage());
            return 0;
        }
    }

    private void increment(String key) {
        try {
            Long n = redis.opsForValue().increment(key);
            if (n != null && n == 1) {
                redis.expire(key, properties.window());   // la ventana empieza con el primer fallo
            }
        } catch (RuntimeException e) {
            log.warn("Login attempt not counted (Redis unavailable): {}", e.getMessage());
        }
    }

    private String accountKey(String email) {
        return "jornal:" + region + ":login-fail:account:" + sha256(email.trim().toLowerCase(Locale.ROOT));
    }

    private String ipKey(String ip) {
        return "jornal:" + region + ":login-fail:ip:" + ip;
    }

    /** El correo no se guarda en claro en Redis. */
    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
