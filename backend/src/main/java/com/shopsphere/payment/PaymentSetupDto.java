package com.shopsphere.payment;

import java.math.BigDecimal;

/**
 * Everything the frontend needs to open Razorpay Checkout for an order.
 */
public record PaymentSetupDto(
        String gatewayOrderId,
        String keyId,
        BigDecimal amount,
        String currency,
        String orderNumber,
        String paymentStatus
) {}
