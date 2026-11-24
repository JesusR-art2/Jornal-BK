package com.jornal.region.dto;

import com.jornal.region.ReviewStatus;

import java.time.LocalDate;

public record HolidayResponse(LocalDate date, String name, ReviewStatus reviewStatus) {
}
