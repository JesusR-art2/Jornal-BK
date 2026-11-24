package com.jornal.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogApi {

    List<CategorySummary> findActiveByIds(Collection<UUID> ids);

    /** Precio de una categoría activa en la región de esta instancia. Vacío si no existe, está inactiva o no tiene precio. */
    Optional<CategoryPriceSummary> priceOf(UUID categoryId);
}
