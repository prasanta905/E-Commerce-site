package com.shopsphere.cart;

import java.util.List;

public record CartItemDto(
        Long itemId,
        Long productId,
        String name,
        String slug,
        String imageUrl,
        double unitPrice,
        double lineTotal,
        int quantity,
        int availableQuantity,
        boolean inStock
) {}
