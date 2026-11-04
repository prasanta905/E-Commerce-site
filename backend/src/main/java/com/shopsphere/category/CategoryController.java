package com.shopsphere.category;

import java.util.List;

import com.shopsphere.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Categories", description = "Public catalog navigation (parent categories + children)")
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "All active parent categories with child counts")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryService.CategorySummary>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.listActive()));
    }

    @Operation(summary = "Category by slug")
    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<CategoryService.CategorySummary>> bySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.bySlug(slug)));
    }
}
