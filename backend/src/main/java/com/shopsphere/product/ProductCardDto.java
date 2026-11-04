package com.shopsphere.product;

import java.math.BigDecimal;
import java.util.List;

/** Grid/list cell DTO — what the storefront tiles render. */
public record ProductCardDto(
        Long id,
        String name,
        String slug,
        BigDecimal sellingPrice,
        BigDecimal mrp,
        int discountPercent,
        double ratingAvg,
        int ratingCount,
        String imageUrl,
        String categoryName,
        String brandName,
        boolean inStock,
        boolean isNew
) {
    public static int discountPercent(BigDecimal mrp, BigDecimal selling) {
        if (mrp == null || selling == null || mrp.compareTo(BigDecimal.ZERO) <= 0) return 0;
        java.math.BigDecimal pct = mrp.subtract(selling)
                .multiply(BigDecimal.valueOf(100))
                .divide(mrp, 0, java.math.RoundingMode.HALF_UP);
        return Math.max(0, pct.intValue());
    }
}
