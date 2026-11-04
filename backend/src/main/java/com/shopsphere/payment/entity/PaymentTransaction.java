package com.shopsphere.payment.entity;

import java.time.Instant;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Append-only ledger of gateway interactions (callbacks, verifications, webhooks).
 * `PAYMENT_W_BH` events rely on razorpay_order_id + signature being stored for audit.
 */
@Entity
@Table(name = "payment_transactions",
       indexes = { @Index(name = "ix_ptx_payment", columnList = "payment_id"),
                   @Index(name = "ix_ptx_gw_order", columnList = "razorpay_order_id") })
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PaymentTransaction {

    public enum PaymentEvent { ORDER_CREATED, CAPTURED, FAILED, WEBHOOK }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "razorpay_order_id", length = 64)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 64)
    private String razorpayPaymentId;

    @Column(name = "signature", length = 255)
    private String signature;

    @Column(name = "event_type", nullable = false, length = 30)
    private PaymentEvent eventType;

    /** Raw webhook/verify payload for dispute resolution (JSON). */
    @Lob
    @Column(name = "payload")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void stamp() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
