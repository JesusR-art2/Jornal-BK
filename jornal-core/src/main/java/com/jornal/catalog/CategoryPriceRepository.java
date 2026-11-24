package com.jornal.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryPriceRepository extends JpaRepository<CategoryPrice, CategoryPrice.Key> {

    List<CategoryPrice> findByCategoryIdInAndRegionCode(Collection<UUID> categoryIds, String regionCode);

    Optional<CategoryPrice> findByCategoryIdAndRegionCode(UUID categoryId, String regionCode);
}
