package com.shopsphere.product;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shopsphere.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Products", description = "Public catalog browsing")
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "Search / filter products with pagination")
    @GetMapping
    public ResponseEntity<ApiResponse<ProductListResponse>> search(
            @Parameter(description = "Free-text name search") @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(ApiResponse.ok(productService.search(
                categoryId, brandId, search, minPrice, maxPrice, minRating, inStock, sort, page, size)));
    }

    @Operation(summary = "Product detail incl. specs, images, related items")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDetailDto>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getDetail(id)));
    }

    @Operation(summary = "Featured products")
    @GetMapping("/featured")
    public ResponseEntity<ApiResponse<ProductListResponse>> featured(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size) {
        return ResponseEntity.ok(ApiResponse.ok(productService.featured(page, size)));
    }

    @Operation(summary = "New arrivals")
    @GetMapping("/new-arrivals")
    public ResponseEntity<ApiResponse<ProductListResponse>> newArrivals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size) {
        return ResponseEntity.ok(ApiResponse.ok(productService.newArrivals(page, size)));
    }

    @Operation(summary = "Best sellers (by review volume)")
    @GetMapping("/best-sellers")
    public ResponseEntity<ApiResponse<ProductListResponse>> bestSellers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size) {
        return ResponseEntity.ok(ApiResponse.ok(productService.bestSellers(page, size)));
    }

    @Operation(summary = "Lightweight search suggestions for the navbar")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<ProductCardDto>>> searchSuggestions(
            @RequestParam(name = "q") String q) {
        return ResponseEntity.ok(ApiResponse.ok(productService.searchSuggestions(q)));
    }

    @Operation(summary = "Products inside a category (includes sub-categories)")
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<ApiResponse<ProductListResponse>> byCategory(
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(ApiResponse.ok(productService.byCategory(categoryId, sort, page, size)));
    }

    @Operation(summary = "Products of a brand")
    @GetMapping("/brand/{brandId}")
    public ResponseEntity<ApiResponse<ProductListResponse>> byBrand(
            @PathVariable Long brandId,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(ApiResponse.ok(productService.byBrand(brandId, sort, page, size)));
    }
}
