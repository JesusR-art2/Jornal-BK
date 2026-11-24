package com.jornal.users.services;

import com.jornal.region.RegionApi;
import com.jornal.region.RegionProfileView;
import com.jornal.shared.Locales;
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

import java.time.Clock;
import java.util.Locale;

/** Crea el administrador inicial de la región si ADMIN_EMAIL y ADMIN_PASSWORD están definidos. */
@Component
@RequiredArgsConstructor
@Slf4j
public class InitialAdminRunner implements ApplicationRunner {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties properties;
    private final RegionApi regionApi;
    private final Clock clock;

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

        RegionProfileView profile = regionApi.current();
        String given = isBlank(properties.givenName()) ? "Admin" : properties.givenName().trim();
        String family = isBlank(properties.familyName()) ? null : properties.familyName().trim();

        User admin = new User();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(properties.password()));
        admin.setGivenName(given);
        admin.setFamilyName(family);
        admin.setName(profile.nameOrder().compose(given, family));
        admin.setRol(Rol.ADMIN);
        admin.setVerificationState(VerificationState.VERIFIED);
        admin.setActive(true);
        admin.setCountryCode(profile.code());
        admin.setLocale(Locales.normalize(properties.locale(), profile.defaultLocale()));
        admin.setTermsVersion(profile.termsVersion());
        admin.setTermsAcceptedAt(clock.instant());
        admin.setPrivacyVersion(profile.privacyVersion());
        admin.setPrivacyAcceptedAt(clock.instant());

        users.save(admin);
        log.info("Initial admin created for region {}: {}", profile.code(), email);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
