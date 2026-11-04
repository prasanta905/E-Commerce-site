package com.shopsphere.order.entity;

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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Append-only order state audit — never updated once written.
 */
@Entity
@Table(name = "order_status_history", indexes = @Index(name = "ix_osh_order", columnList = "order_id"))
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class OrderStatusHistory {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false) // set via addToHistory helper on Order or service
    private Order order;

    /** null for the first entry (creation). */
    @Column(name = "previous_status", length = 30)
    private OrderStatus previousStatus;

    @Column(name = "new_status", nullable = false, length = 30)
    private OrderStatus newStatus;

    /** Free-form actor: "system", "admin:<email>", "customer:<email>". */
    @Column(name = "changed_by", nullable = false, length = 60)
    private String changedBy;

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void stamp() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public void attachTo(Order o) { this.order = o; }
}
