package com.jornal.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryTranslationRepository extends JpaRepository<CategoryTranslation, UUID> {

    List<CategoryTranslation> findByCategoryIdInAndLocaleIn(Collection<UUID> ids, Collection<String> locales);

    Optional<CategoryTranslation> findByCategoryIdAndLocale(UUID categoryId, String locale);
}
