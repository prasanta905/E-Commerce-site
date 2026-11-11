package com.shopsphere.coupon.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopsphere.coupon.entity.Coupon;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByCodeIgnoreCaseAndIsActiveTrue(String code);
    boolean existsByCodeIgnoreCase(String code);
    Optional<Coupon> findById(Long id);
    java.util.List<Coupon> findAllByDeletedAtIsNullOrderByCreatedAtDesc();

    /** Count of distinct successful redemptions by one user for one code (within checkout tx). */
    @Query("""
            SELECT COUNT(cr) FROM CouponRedemption cr
            WHERE cr.coupon.id = :couponId AND cr.user.id = :userId
            """)
    long countUserRedemptions(@Param("couponId") Long couponId, @Param("userId") Long userId);
}
