package com.shopsphere.checkout;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST /checkout — server recomputes all prices from the cart; the client may
 * only choose an address/shipping/coupon/payment. Optional idempotencyKey
 * guards against double-click duplicates.
 */
public record CheckoutRequest(
        @NotNull Long addressId,
        @NotNull Long shippingMethodId,
        String couponCode,
        @NotNull @Pattern(regexp = "COD|RAZORPAY|UPI", message = "paymentMethod must be COD, RAZORPAY or UPI")
        String paymentMethod,
        @Size(max = 64) String idempotencyKey
) {}
