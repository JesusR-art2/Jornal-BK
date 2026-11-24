package com.jornal.catalog;

import com.jornal.catalog.dto.CategoryRequest;
import com.jornal.catalog.dto.CategoryResponse;
import com.jornal.catalog.dto.UpdateCategoryRequest;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Category toEntity(CategoryRequest request);

    CategoryResponse toResponse(Category category);

    List<CategoryResponse> toResponses(List<Category> categories);

    CategorySummary toSummary(Category category);

    List<CategorySummary> toSummaries(List<Category> categories);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name", source ="name")
    @Mapping(target = "description", source ="description")
    void update(UpdateCategoryRequest request, @MappingTarget Category category);

}
