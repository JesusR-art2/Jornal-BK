package com.jornal.catalog;

import org.springframework.data.repository.query.Param;
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

    @Query(value = """
    SELECT c.* FROM categories c
    WHERE c.active = true AND (
        unaccent(lower(c.slug)) LIKE unaccent(lower(:pattern)) ESCAPE '\\'
        OR EXISTS (
            SELECT 1 FROM category_translations t
            WHERE t.category_id = c.id
              AND unaccent(lower(t.name)) LIKE unaccent(lower(:pattern)) ESCAPE '\\'
        )
    )
    ORDER BY c.name
    LIMIT :limit
    """, nativeQuery = true)
    List<Category> searchActive(@Param("pattern") String pattern, @Param("limit") int limit);

    List<Category> findByIdInAndActiveTrue(Collection<UUID> ids);

    boolean existsBySlug(String slug);

}


