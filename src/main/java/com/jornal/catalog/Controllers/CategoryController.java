package com.jornal.catalog.Controllers;


import com.jornal.catalog.CategoryService;
import com.jornal.catalog.DTO.CategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public List<CategoryResponse> findActive(@RequestParam(required = false) String q) {
        return categoryService.findActive(q);
    }
}
