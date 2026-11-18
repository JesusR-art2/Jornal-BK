package com.jornal.users.services;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("jornal.admin")
public record AdminProperties(String email, String password, String name,
                              String country, String locale) {
}
