package com.shopsphere.review.entity;

import com.shopsphere.common.entity.TimestampedEntity;
import com.shopsphere.order.entity.Order;
import com.shopsphere.product.entity.Product;
import com.shopsphere.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One review per user per product (DB-enforced). order_id links to a delivered
 * order line to allow the verified-purchase badge; null = unverified review.
 */
@Entity
@Table(name = "reviews",
       uniqueConstraints = @UniqueConstraint(name = "ux_reviews_prod_user", columnNames = { "product_id", "user_id" }),
       indexes = @Index(name = "ix_reviews_prod", columnList = "product_id"))
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Review extends TimestampedEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Order used to prove delivery — immutable after creation. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "rating", nullable = false)
    private Integer rating; // 1..5 checked at service boundary

    @Column(name = "title", length = 120)
    private String title;

    @Lob
    @Column(name = "body")
    private String body;

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false;
}
