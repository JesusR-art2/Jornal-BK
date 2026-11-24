package com.jornal.users.services;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Administrador inicial. Se crea en la región de esta instancia (APP_REGION). */
@ConfigurationProperties("jornal.admin")
public record AdminProperties(String email, String password, String givenName,
                              String familyName, String locale) {
}
