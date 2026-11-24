package com.jornal.users;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("jornal.security.refresh")
public record RefreshProperties(Duration ttl) {
}
