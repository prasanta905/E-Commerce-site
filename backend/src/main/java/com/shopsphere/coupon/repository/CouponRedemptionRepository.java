package com.shopsphere.coupon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopsphere.coupon.entity.CouponRedemption;
import jakarta.persistence.LockModeType;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {
    long countByCouponId(Long couponId);

    /** Serialise concurrent redemptions of the same coupon in checkout txs. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT COUNT(cr) FROM CouponRedemption cr WHERE cr.coupon.id = :couponId")
    long countByCouponIdForUpdate(@Param("couponId") Long couponId);

    boolean existsByCouponIdAndOrderId(Long couponId, Long orderId);
}
