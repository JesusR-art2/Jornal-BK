package com.jornal.Catalog.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record CategoryRequest(
        @NotBlank  @Size(max = 60)
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                 message = "Slug must contain lowercase letters, numbers and hyphens only")
        String slug,

        @NotBlank @Size(max = 100) String name,

        @Size(max = 255) String description

) {
    public CategoryRequest{
        slug = slug == null ? null : slug.trim().toLowerCase();
        name = name == null ? null : name.trim();
        description = description == null || description.isBlank() ? null : description.trim();
    }
}
