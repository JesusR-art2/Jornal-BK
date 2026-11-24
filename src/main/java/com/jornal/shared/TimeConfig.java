package com.jornal.shared;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Única fuente de la hora actual. Las pruebas reemplazan este bean por un {@code Clock.fixed(...)}.
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
