package com.jornal.Catalog;

import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    @Query("""
              select  c from Category c
              where (:active is null or c.active = :active)
                  and (lower(c.name) like :pattern or lower (c.slug) like :pattern)
           """)
    Page<Category> findAll(@Param("active") Boolean active,
                          @Param("pattern") String pattern,
                          Pageable pageable);

    List<Category> findByIdInAndActiveTrue(Collection<UUID> ids);

    boolean existsBySlug(String slug);

}


