package com.jornal.Catalog;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CatalogApi {
    List<CategorySummary> findActiveByIds(Collection<UUID> ids);
}
