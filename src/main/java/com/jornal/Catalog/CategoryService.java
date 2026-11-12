package com.jornal.Catalog;

import com.jornal.Catalog.DTO.CategoryRequest;
import com.jornal.Catalog.DTO.CategoryResponse;
import com.jornal.Catalog.DTO.UpdateCategoryRequest;
import com.jornal.Catalog.enums.ErrorCatalog;
import com.jornal.Shared.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService implements CatalogApi{

    private final CategoryRepository repository;
    private final CategoryMapper mapper;

    @Transactional(readOnly = true)
    public List<CategoryResponse> findActive(){
        return mapper.toResponses(repository.findByActiveTrueOrderByNameAsc());
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll(){
        return mapper.toResponses(repository.findAllByOrderByNameAsc());
    }

    @Transactional(readOnly = true)
    public CategoryResponse findById(UUID id){
        return mapper.toResponse(getOrThrow(id));
    }

    @Transactional
    public CategoryResponse save(CategoryRequest request){
        if (repository.existsBySlug(request.slug())){
            throw new ApiException(ErrorCatalog.CAT002_SLUG_ALREADY_EXISTS, request.slug());
        }
        Category category = mapper.toEntity(request);
        return mapper.toResponse(repository.save(category));
    }

    @Transactional
    public CategoryResponse update(UUID id, UpdateCategoryRequest request){
        Category category = getOrThrow(id);
        mapper.update(request, category);
        return mapper.toResponse(category);
    }

    private Category getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCatalog.CAT001_CATEGORY_NOTFOUND, id));
    }

    @Transactional
    public void deactivate(UUID id){
        getOrThrow(id).setActive(false);
    }

    @Transactional
    public void activate(UUID id){
        getOrThrow(id).setActive(true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategorySummary> findActiveByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return mapper.toSummaries(repository.findByIdInAndActiveTrue(ids));
    }
}
