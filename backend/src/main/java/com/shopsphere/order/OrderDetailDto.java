package com.shopsphere.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.shopsphere.common.PageResponse;

public record OrderDetailDto(
        Long id,
        String orderNumber,
        String status,
        String paymentMethod,
        String paymentStatus,
        Map<String, String> addressSnapshot,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal shippingFee,
        BigDecimal totalAmount,
        Instant createdAt,
        String cancelReason,
        String trackingNumber,
        String courier,
        List<ItemLine> items,
        List<StatusEvent> statusHistory,
        List<CouponEvent> coupons
) {
    public record ItemLine(Long productId, String name, String imageUrl, int quantity, double unitPrice) {}
    public record StatusEvent(String from, String to, String actor, String note, Instant at) {}
    public record CouponEvent(String code) {}
}
