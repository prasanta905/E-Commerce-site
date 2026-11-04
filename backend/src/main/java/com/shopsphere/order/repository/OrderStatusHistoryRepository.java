package com.shopsphere.order.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopsphere.order.entity.OrderStatusHistory;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {
    List<OrderStatusHistory> findByOrderIdOrderByCreatedAtAsc(Long orderId);
}
