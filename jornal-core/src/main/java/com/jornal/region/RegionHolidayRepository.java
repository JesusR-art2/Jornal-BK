package com.jornal.region;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegionHolidayRepository extends JpaRepository<RegionHoliday, UUID> {

    List<RegionHoliday> findByRegionCodeAndHolidayDateBetweenOrderByHolidayDate(String regionCode, LocalDate from, LocalDate to);

    Optional<RegionHoliday> findByRegionCodeAndHolidayDate(String regionCode, LocalDate date);
}
