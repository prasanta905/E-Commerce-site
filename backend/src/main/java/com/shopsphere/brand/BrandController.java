package com.shopsphere.brand;

import java.util.List;

import com.shopsphere.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Brands", description = "Public brand listing")
@RestController
@RequestMapping("/api/v1/brands")
public class BrandController {

    private final BrandService brandService;

    public BrandController(BrandService brandService) {
        this.brandService = brandService;
    }

    @Operation(summary = "All active brands")
    @GetMapping
    public ResponseEntity<ApiResponse<List<BrandService.BrandSummary>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(brandService.listActive()));
    }

    @Operation(summary = "Brand by slug")
    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<BrandService.BrandSummary>> bySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.ok(brandService.bySlug(slug)));
    }
}
