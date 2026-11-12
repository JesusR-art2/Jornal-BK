package com.jornal.Catalog.Controllers;


import com.jornal.Catalog.CategoryService;
import com.jornal.Catalog.DTO.CategoryRequest;
import com.jornal.Catalog.DTO.CategoryResponse;
import com.jornal.Catalog.DTO.UpdateCategoryRequest;
import com.jornal.Shared.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/categories")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public PageResponse<CategoryResponse> list(@RequestParam(required = false) Boolean active,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by("name").ascending());
        return categoryService.findAll(active, q, pageable);
    }

    @GetMapping("/{id}")
    public CategoryResponse get(@PathVariable UUID id){
        return categoryService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse save(@Valid @RequestBody CategoryRequest request){
        return categoryService.save(request);
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable UUID id,
                                   @Valid @RequestBody UpdateCategoryRequest request){
        return categoryService.update(id, request);
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id) {
        categoryService.deactivate(id);
    }

    @PostMapping("/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activate(@PathVariable UUID id) {
        categoryService.activate(id);
    }

}
