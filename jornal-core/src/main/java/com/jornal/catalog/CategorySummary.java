package com.jornal.catalog;

import java.util.UUID;

public record CategorySummary(UUID id, String slug, String name) {
}
