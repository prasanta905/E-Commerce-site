package com.shopsphere.payment;

import java.math.BigDecimal;

/**
 * Redirect parameters for the YouPI hosted UPI checkout.
 *
 * {@code paymentStatus} is ShopSphere's own state and only becomes PAID after
 * the signed webhook reports a reconciled settlement. {@code gatewayStatus} is
 * informational and must never be used to fulfill an order on its own.
 */
public record YoupiSessionDto(
        String paymentId,
        String checkoutUrl,
        BigDecimal amount,
        String currency,
        String orderNumber,
        String paymentStatus,
        String gatewayStatus
) {}
