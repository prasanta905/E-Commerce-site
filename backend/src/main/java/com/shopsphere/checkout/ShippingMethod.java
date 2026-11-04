package com.shopsphere.checkout;

/** Free shipping above threshold — concrete business rule of the store. */
public record ShippingMethod(
        Long id,
        String code,
        String name,
        java.math.BigDecimal baseFee,
        java.math.BigDecimal freeAbove
) {}
