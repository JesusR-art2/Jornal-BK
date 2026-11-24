package com.jornal.region.dto;

import com.jornal.region.ReviewStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HolidayRequest(@NotBlank @Size(max = 120) String name, ReviewStatus reviewStatus) {
}
