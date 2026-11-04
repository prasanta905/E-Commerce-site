package com.shopsphere.checkout;

import java.math.BigDecimal;
import java.util.List;

import com.shopsphere.common.PageResponse;

/** POST /checkout/preview — client-side quote BEFORE order creation. */
public record CheckoutPreviewDto(
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal shippingFee,
        BigDecimal total,
        String couponCode,
        String shippingMethodName,
        List<String> stockIssues
) {}
