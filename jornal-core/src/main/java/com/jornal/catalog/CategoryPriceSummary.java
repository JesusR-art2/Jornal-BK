package com.jornal.catalog;

import java.util.UUID;

/** Categoría activa con su precio en la región de esta instancia; {@code name} en el idioma de la petición. */
public record CategoryPriceSummary(UUID categoryId, String slug, String name, String unit,
                                   long amountMinor, String currency) {
}
