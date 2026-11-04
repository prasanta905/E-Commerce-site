package com.shopsphere.product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ProductDetailDto(
        Long id,
        String name,
        String slug,
        String description,
        BigDecimal sellingPrice,
        BigDecimal mrp,
        int discountPercent,
        double ratingAvg,
        int ratingCount,
        List<String> imageUrls,
        Map<String, String> specifications,
        Long categoryId,
        String categoryName,
        Long brandId,
        String brandName,
        boolean featured,
        int availableQuantity,
        int lowStockThreshold,
        boolean inStock,
        List<ProductCardDto> relatedProducts
) {}
