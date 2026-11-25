package com.jornal.region.services;

import com.jornal.region.RegionApi;
import com.jornal.region.RegionProfileView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Falla el arranque si APP_REGION no tiene perfil: una instancia sin perfil no puede atender a nadie. */
@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class RegionStartupCheck implements ApplicationRunner {

    private final RegionApi regions;
    private final RegionService service;

    @Override
    public void run(ApplicationArguments args) {
        service.registerProfileLocales();
        RegionProfileView profile = regions.current();
        log.info("Serving region {} (locale {}, currency {}, profile v{} {})", profile.code(),
                profile.defaultLocale(), profile.currency(), profile.version(), profile.reviewStatus());
    }
}
