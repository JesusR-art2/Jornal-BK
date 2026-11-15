package com.jornal.catalog.DTO;

import java.util.UUID;

public record CategoryResponse(UUID id, String slug, String name,
                               String description, boolean active) {
}
