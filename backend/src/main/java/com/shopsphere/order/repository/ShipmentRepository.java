package com.shopsphere.order.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopsphere.order.entity.Shipment;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
    Optional<Shipment> findByOrderId(Long orderId);
}
