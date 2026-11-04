package com.shopsphere.checkout;

/** POST /checkout — creates the order + (for RAZORPAY) the gateway order id. */
public record CheckoutResponse(
        Long orderId,
        String orderNumber,
        String status,          // PENDING or CONFIRMED (COD)
        String razorpayOrderId, // non-null only for RAZORPAY
        String razorpayKeyId
) {}
