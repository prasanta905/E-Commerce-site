package com.shopsphere.payment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopsphere.payment.entity.PaymentTransaction;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    List<PaymentTransaction> findByPaymentIdOrderByCreatedAtDesc(Long paymentId);

    /** Webhook idempotency — same razorpay payment captured only once. */
    Optional<PaymentTransaction> findFirstByRazorpayPaymentIdAndEventType(
            String razorpayPaymentId, PaymentTransaction.PaymentEvent eventType);
}
