package com.jornal.console;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @param services    servicios a vigilar: {@code nombre=url-de-salud} separados por coma
 *                    (por ejemplo {@code payments=http://payments:8080/actuator/health})
 * @param rabbitApi   API de gestión de RabbitMQ ({@code http://rabbitmq:15672/api})
 * @param rabbitUser  usuario de la API de gestión
 * @param rabbitPassword contraseña de la API de gestión
 */
@ConfigurationProperties("jornal.console")
public record ConsoleProperties(String services, String rabbitApi, String rabbitUser, String rabbitPassword) {

    public Map<String, String> serviceUrls() {
        Map<String, String> urls = new LinkedHashMap<>();
        if (services == null) return urls;
        for (String entry : services.split(",")) {
            int eq = entry.indexOf('=');
            if (eq > 0) urls.put(entry.substring(0, eq).trim(), entry.substring(eq + 1).trim());
        }
        return urls;
    }
}
