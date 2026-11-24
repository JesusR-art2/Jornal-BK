package com.jornal.region.controllers;

import com.jornal.region.RegionProfileView;
import com.jornal.region.dto.HolidayResponse;
import com.jornal.region.services.RegionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Perfil regional público: el frontend lo usa para formatos, formularios y medios de pago. */
@RestController
@RequestMapping("/region-profile")
@RequiredArgsConstructor
public class RegionController {

    private final RegionService regionService;
    private final Clock clock;

    @GetMapping
    public RegionProfileView profile() {
        return regionService.current();
    }

    @GetMapping("/holidays")
    public List<HolidayResponse> holidays(@RequestParam(required = false) Integer year) {
        int y = year != null ? year
                : LocalDate.now(clock.withZone(ZoneId.of(regionService.current().defaultTimezone()))).getYear();
        return regionService.holidays(y);
    }
}
