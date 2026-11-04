package com.shopsphere.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Customer order summary row. */
public record OrderSummaryDto(
        Long id,
        String orderNumber,
        String status,
        String paymentMethod,
        BigDecimal totalAmount,
        Instant createdAt,
        List<ItemLine> items
) {
    public record ItemLine(Long productId, String name, String imageUrl, int quantity, double unitPrice) {}
}
