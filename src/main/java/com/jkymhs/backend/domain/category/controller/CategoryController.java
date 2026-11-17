package com.jkymhs.backend.domain.category.controller;

import com.jkymhs.backend.domain.category.dto.request.CategoryListRequest;
import com.jkymhs.backend.domain.category.dto.response.CategoryListResponse;
import com.jkymhs.backend.domain.category.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<CategoryListResponse> getCategories(@ModelAttribute CategoryListRequest request) {
        return ResponseEntity.ok(CategoryListResponse.from(categoryService.getFilterCategories(request.tab())));
    }
}
