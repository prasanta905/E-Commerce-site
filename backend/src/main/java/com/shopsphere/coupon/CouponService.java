package com.shopsphere.coupon;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.common.exception.BusinessException;
import com.shopsphere.coupon.entity.Coupon;
import com.shopsphere.coupon.repository.CouponRepository;

/**
 * Coupon calculation + validation. Redemptions are written during checkout
 * (CouponRedemptionLedger) — this service computes discount for preview and
 * validates a coupon code BEFORE order creation.
 */
@Service
public class CouponService {

    private final CouponRepository couponRepository;

    public CouponService(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    /** Validates (activeness, expiry, min-order) and computes the rupee discount. */
    @Transactional(readOnly = true)
    public CouponApplication apply(String code, BigDecimal subtotal, Long userId) {
        Coupon coupon = couponRepository.findByCodeIgnoreCaseAndIsActiveTrue(code.trim())
                .orElseThrow(() -> BusinessException.badRequest("COUPON_INVALID", "Coupon code is not valid"));

        if (coupon.getDeletedAt() != null || !coupon.isUsable()) {
            throw BusinessException.badRequest("COUPON_INACTIVE", "This coupon is no longer active");
        }
        if (coupon.getExpiresAt().isBefore(Instant.now())) {
            throw BusinessException.badRequest("COUPON_EXPIRED", "This coupon has expired");
        }
        if (subtotal.compareTo(coupon.getMinOrderAmount()) < 0) {
            throw BusinessException.badRequest("COUPON_MIN_NOT_MET",
                    "Minimum order amount for this coupon is ₹" + coupon.getMinOrderAmount() + "");
        }
        if (userId != null && couponRepository.countUserRedemptions(coupon.getId(), userId) >= firstNon(coupon.getMaxUsesPerUser(), 1)) {
            throw BusinessException.badRequest("COUPON_ALREADY_USED",
                    "You've already reached this coupon's usage limit");
        }
        // NOTE: total-global-uses is enforced atomically at checkout with a locked count.

        BigDecimal discount = switch (coupon.getDiscountType()) {
            case PERCENT -> subtotal.multiply(coupon.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            case FIXED -> coupon.getDiscountValue();
        };
        if (coupon.getDiscountType() == com.shopsphere.coupon.entity.DiscountType.PERCENT
                && coupon.getMaxDiscountAmount() != null) {
            discount = discount.min(coupon.getMaxDiscountAmount());
        }
        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal; // never exceed subtotal
        }

        return new CouponApplication(coupon.getId(), coupon.getCode(), discount,
                "Applied " + coupon.getCode() + " (−₹" + discount + ")");
    }

    /**
     * Entity-level lookup used by checkout when attaching the coupon to an
     * order. Applies the same validation as {@link #apply} minus money math.
     */
    @Transactional(readOnly = true)
    public Coupon findActiveByCode(String code) {
        return couponRepository.findByCodeIgnoreCaseAndIsActiveTrue(code.trim())
                .filter(c -> c.getDeletedAt() == null && c.isUsable())
                .orElseThrow(() -> BusinessException.badRequest("COUPON_INVALID", "Coupon code is not valid"));
    }

    private static int firstNon(Integer value, int fallback) {
        return value == null ? fallback : value;
    }
}
