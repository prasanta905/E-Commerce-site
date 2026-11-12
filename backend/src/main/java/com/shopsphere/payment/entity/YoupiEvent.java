package com.shopsphere.payment.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Append-only audit ledger for YouPI. Webhook rows are deduplicated by
 * {@code delivery_id}; the unique constraint is the idempotency guard, so a
 * replayed delivery can never be settled twice.
 */
@Entity
@Table(name = "youpi_events",
       uniqueConstraints = @UniqueConstraint(name = "ux_youpi_delivery", columnNames = "delivery_id"),
       indexes = { @Index(name = "ix_youpi_event_order", columnList = "order_reference"),
                   @Index(name = "ix_youpi_event_payment", columnList = "payment_id") })
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class YoupiEvent {

    public static final String TYPE_SESSION_CREATED = "SESSION_CREATED";
    public static final String TYPE_WEBHOOK = "WEBHOOK";

    public static final String RESULT_SESSION_CREATED = "SESSION_CREATED";
    public static final String RESULT_SETTLED = "SETTLED";
    public static final String RESULT_OBSERVED = "OBSERVED";
    public static final String RESULT_IGNORED = "IGNORED";
    public static final String RESULT_REJECTED = "REJECTED";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** X-Gateway-Delivery-Id; null for session-creation rows. */
    @Column(name = "delivery_id", length = 64)
    private String deliveryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "event", length = 32)
    private String event;

    @Column(name = "method", length = 32)
    private String method;

    @Column(name = "settlement_state", length = 32)
    private String settlementState;

    @Column(name = "result", nullable = false, length = 20)
    private String result;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "order_reference", nullable = false, length = 64)
    private String orderReference;

    @Column(name = "provider_payment_id", length = 64)
    private String providerPaymentId;

    @Column(name = "amount", precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "utr", length = 32)
    private String utr;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @PrePersist
    void stamp() {
        if (receivedAt == null) receivedAt = Instant.now();
    }
}
