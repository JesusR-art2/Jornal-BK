package com.jornal.users;


import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("jornal.legal")
public record LegalProperties(String termsVersion, String privacyVersion) {
}
