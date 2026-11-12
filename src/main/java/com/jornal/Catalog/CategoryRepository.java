package com.jornal.Catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByActiveTrueOrderByNameAsc();

    List<Category> findAllByOrderByNameAsc();

    List<Category> findByIdInAndActiveTrue(Collection<UUID> ids);

    boolean existsBySlug(String slug);
}


