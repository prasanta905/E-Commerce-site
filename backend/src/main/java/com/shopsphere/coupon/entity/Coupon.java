package com.shopsphere.coupon.entity;

import java.math.BigDecimal;
import java.time.Instant;

import com.shopsphere.common.entity.TimestampedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "coupons", uniqueConstraints = @UniqueConstraint(name = "ux_coupons_code", columnNames = "code"),
       indexes = @Index(name = "ix_coupons_active", columnList = "is_active"))
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Coupon extends TimestampedEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "discount_type", nullable = false, length = 10)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "min_order_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal minOrderAmount = BigDecimal.ZERO;

    /** Cap applicable only when discountType == PERCENT. */
    @Column(name = "max_discount_amount", precision = 10, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** null = unlimited global uses. */
    @Column(name = "max_global_uses")
    private Integer maxGlobalUses;

    @Column(name = "max_uses_per_user")
    @Builder.Default
    private Integer maxUsesPerUser = 1;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /** Soft delete so redemption history remains referencable. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    public boolean isUsable() {
        return isActive && deletedAt == null && expiresAt.isAfter(Instant.now());
    }
}
