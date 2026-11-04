package com.shopsphere.coupon.entity;

import java.time.Instant;

import com.shopsphere.common.entity.TimestampedEntity;
import com.shopsphere.order.entity.Order;
import com.shopsphere.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Redemption ledger. The UNIQUE(coupon_id, order_id) protects double-redemption
 * of the same coupon on the same order; per-user quota is enforced with a
 * counted query inside the checkout transaction.
 */
@Entity
@Table(name = "coupon_redemptions",
       uniqueConstraints = @UniqueConstraint(name = "ux_cr_coupon_order", columnNames = { "coupon_id", "order_id" }),
       indexes = { @Index(name = "ix_cr_user", columnList = "user_id, coupon_id") })
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponRedemption extends TimestampedEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_id", nullable = false)
    @Setter(AccessLevel.NONE)
    private Coupon coupon;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    void attach(Coupon c) { this.coupon = c; }

    /** Single construction path for checkout. */
    public static CouponRedemption create(Coupon coupon, User user, Order order) {
        CouponRedemption redemption = new CouponRedemption();
        redemption.attach(coupon);
        redemption.setUser(user);
        redemption.setOrder(order);
        return redemption;
    }
}
