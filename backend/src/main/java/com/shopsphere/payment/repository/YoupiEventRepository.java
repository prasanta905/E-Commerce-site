package com.shopsphere.payment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopsphere.payment.entity.YoupiEvent;

public interface YoupiEventRepository extends JpaRepository<YoupiEvent, Long> {
    boolean existsByDeliveryId(String deliveryId);
    boolean existsByOrderReferenceAndProviderPaymentIdAndEventType(
            String orderReference, String providerPaymentId, String eventType);
    Optional<YoupiEvent> findByDeliveryId(String deliveryId);
    long countByDeliveryId(String deliveryId);
    long countByOrderReference(String orderReference);
    List<YoupiEvent> findByOrderReferenceOrderByReceivedAtAsc(String orderReference);
}
