package com.jornal.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(
        @NotBlank  @Size(max = 100) String name,
        @Size(max = 255) String description
) {
    public UpdateCategoryRequest {
        name = name == null ? null : name.trim();
        description = description == null || description.isBlank() ? null : description.trim();
    }
}
