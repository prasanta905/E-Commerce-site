package com.shopsphere.payment.entity;

import java.math.BigDecimal;
import java.time.Instant;

import com.shopsphere.common.entity.TimestampedEntity;
import com.shopsphere.order.entity.Order;
import com.shopsphere.order.entity.PaymentMethod;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payments",
       uniqueConstraints = {
               @UniqueConstraint(name = "ux_payments_order", columnNames = "order_id"),
               @UniqueConstraint(name = "ux_payments_gw_order", columnNames = "gateway_order_id") },
       indexes = @Index(name = "ix_payments_gw_order", columnList = "gateway_order_id"))
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Payment extends TimestampedEntity {

    public enum PaymentStatus { PENDING, PAID, FAILED, REFUNDED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 15)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 15)
    @Builder.Default
    private PaymentProvider provider = PaymentProvider.RAZORPAY;

    /**
     * Gateway session id — Razorpay `order.id` or YouPI `paymentId`.
     * Unique key for idempotent creation and webhook lookup.
     */
    @Column(name = "gateway_order_id", unique = true, length = 64)
    private String gatewayOrderId;

    /** Hosted checkout URL returned by YouPI (null for Razorpay). */
    @Column(name = "checkout_url", length = 1024)
    private String checkoutUrl;

    @Column(name = "razorpay_payment_id", length = 64)
    private String razorpayPaymentId;

    /** Bank UTR recorded only after YouPI reports a reconciled UPI settlement. */
    @Column(name = "utr", length = 32)
    private String utr;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_at")
    private Instant paidAt;
}
