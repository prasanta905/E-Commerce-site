package com.shopsphere.order.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopsphere.order.entity.ReturnRequest;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {
    Optional<ReturnRequest> findByOrderId(Long orderId);

    Page<ReturnRequest> findByStatusOrderByCreatedAtDesc(ReturnStatus status);

    Page<ReturnRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<ReturnRequest> findByStatusOrderByCreatedAtDesc(ReturnStatus status, Pageable pageable);

    long countByStatus(ReturnStatus status);
}
