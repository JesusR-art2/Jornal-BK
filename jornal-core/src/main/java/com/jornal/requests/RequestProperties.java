package com.jornal.requests;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Plazos de la saga ({@code jornal.requests.*}).
 *
 * @param paymentTimeout   pedido sin respuesta de pagos: se cancela (por defecto 2 min)
 * @param autoConfirmAfter trabajo terminado sin confirmación del cliente: se confirma solo (24 h; 2 min en demo)
 */
@ConfigurationProperties("jornal.requests")
public record RequestProperties(Duration paymentTimeout, Duration autoConfirmAfter) {

    public RequestProperties {
        if (paymentTimeout == null) paymentTimeout = Duration.ofMinutes(2);
        if (autoConfirmAfter == null) autoConfirmAfter = Duration.ofHours(24);
    }
}
