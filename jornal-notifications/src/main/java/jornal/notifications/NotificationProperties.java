package jornal.notifications;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param coreUrl         URL del core de cada región; {@code {region}} se reemplaza por el código (co, br...)
 * @param internalToken   token de las consultas internas al core
 * @param contactAttempts intentos al pedir el contacto antes de rendirse (el mensaje se reintenta después)
 * @param contactBackoff  espera base entre intentos (crece con cada intento)
 * @param mailFrom        remitente del correo
 */
@ConfigurationProperties("jornal.notifications")
public record NotificationProperties(String coreUrl, String internalToken, int contactAttempts,
                                     Duration contactBackoff, String mailFrom) {
}
