package com.jornal.catalog.controllers;


import com.jornal.catalog.CategoryService;
import com.jornal.catalog.dto.CategoryView;
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
    public List<CategoryView> findActive(@RequestParam(required = false) String q) {
        return categoryService.findActive(q);
    }
}
