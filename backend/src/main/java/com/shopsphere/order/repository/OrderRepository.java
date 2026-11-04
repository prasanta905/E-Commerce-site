package com.shopsphere.order.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopsphere.order.entity.Order;
import com.shopsphere.order.entity.OrderStatus;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByIdAndUserId(Long id, Long userId);

    Optional<Order> findByOrderNumber(String orderNumber);

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // ------------------------------------------------------------ admin
    long countByStatus(OrderStatus status);

    long countByStatusAndCreatedAtAfter(OrderStatus status, java.time.Instant after);

    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o
            WHERE o.status <> com.shopsphere.order.entity.OrderStatus.CANCELLED
            """)
    java.math.BigDecimal sumRevenueTotal();

    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o
            WHERE o.status <> com.shopsphere.order.entity.OrderStatus.CANCELLED
              AND o.createdAt >= :after
            """)
    java.math.BigDecimal sumRevenueSince(@Param("after") java.time.Instant after);

    /** Raw feed for analytics aggregation (Java-side bucketing handles day/week/month). */
    java.util.List<Order> findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(java.time.Instant after);

    @Query("""
            SELECT o FROM Order o
            JOIN o.user u
            WHERE (:status IS NULL OR o.status = :status)
              AND (:search IS NULL
                   OR LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY o.createdAt DESC
            """)
    Page<Order> searchAdmin(@Param("status") String status,
                            @Param("search") String search,
                            Pageable pageable);

    boolean existsByIdAndUserId(Long id, Long userId);

    /** Verified-purchase proof: caller's DELIVERED orders containing this product. */
    @Query("""
            SELECT DISTINCT o FROM Order o
            JOIN o.items i
            WHERE o.user.id = :userId
              AND o.status = com.shopsphere.order.entity.OrderStatus.DELIVERED
              AND i.product.id = :productId
            """)
    java.util.List<Order> findDeliveredByUserAndProduct(@Param("userId") Long userId,
                                                       @Param("productId") Long productId);
}
