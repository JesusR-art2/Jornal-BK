package com.jornal.users.services;

import com.jornal.shared.Locales;
import com.jornal.users.Country;
import com.jornal.users.CountryRepository;
import com.jornal.users.Rol;
import com.jornal.users.User;
import com.jornal.users.UserRepository;
import com.jornal.users.enums.VerificationState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
public class InitialAdminRunner implements ApplicationRunner {

    private final UserRepository users;
    private final CountryRepository countries;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (isBlank(properties.email()) || isBlank(properties.password())) {
            log.debug("Initial admin bootstrap skipped: jornal.admin.email/password not configured");
            return;
        }

        String email = properties.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) {
            log.info("Initial admin already exists: {}", email);
            return;
        }

        Country country = countries.findByCodeAndEnabledTrue(defaultCountry())
                .orElseGet(() -> countries.findByEnabledTrueOrderByName().stream()
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "Cannot create initial admin: no enabled country configured")));

        User admin = new User();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(properties.password()));
        admin.setName(isBlank(properties.name()) ? "Administrator" : properties.name().trim());
        admin.setRol(Rol.ADMIN);
        admin.setVerificationState(VerificationState.VERIFIED);
        admin.setActive(true);
        admin.setCountryCode(country.getCode());
        admin.setLocale(isBlank(properties.locale())
                ? Locales.normalize(country.getDefaultLocale())
                : Locales.normalize(properties.locale()));

        users.save(admin);
        log.info("Initial admin created: {}", email);
    }

    private String defaultCountry() {
        return isBlank(properties.country()) ? "CO" : properties.country().trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
