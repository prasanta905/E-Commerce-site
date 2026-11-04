package com.shopsphere.coupon;

import java.math.BigDecimal;

/** Result of applying a coupon to a cart subtotal. */
public record CouponApplication(
        Long couponId,
        String code,
        BigDecimal discountAmount,
        String explanation
) {}
