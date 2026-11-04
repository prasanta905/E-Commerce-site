package com.shopsphere.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopsphere.payment.entity.Refund;

public interface RefundRepository extends JpaRepository<Refund, Long> {
}
