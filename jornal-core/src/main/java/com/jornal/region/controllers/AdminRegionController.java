package com.jornal.region.controllers;

import com.jornal.region.RegionProfileView;
import com.jornal.region.dto.HolidayRequest;
import com.jornal.region.dto.HolidayResponse;
import com.jornal.region.dto.RegionProfileRequest;
import com.jornal.region.services.RegionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** Edición del perfil de la región que atiende esta instancia. */
@RestController
@RequestMapping("/admin/region-profile")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminRegionController {

    private final RegionService regionService;

    @PutMapping
    public RegionProfileView update(@Valid @RequestBody RegionProfileRequest request) {
        return regionService.update(request);
    }

    @PutMapping("/holidays/{date}")
    public HolidayResponse upsertHoliday(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                         @Valid @RequestBody HolidayRequest request) {
        return regionService.upsertHoliday(date, request);
    }

    @DeleteMapping("/holidays/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHoliday(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        regionService.deleteHoliday(date);
    }
}
