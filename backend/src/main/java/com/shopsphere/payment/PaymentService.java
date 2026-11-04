package com.shopsphere.payment;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.razorpay.RazorpayClient;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.common.exception.BusinessException;
import com.shopsphere.common.exception.ResourceNotFound;
import com.shopsphere.inventory.entity.InventoryTransactionType;
import com.shopsphere.inventory.repository.InventoryRepository;
import com.shopsphere.inventory.repository.InventoryTransactionRepository;
import com.shopsphere.notification.NotificationSaver;
import com.shopsphere.order.entity.Order;
import com.shopsphere.order.entity.OrderItem;
import com.shopsphere.order.entity.OrderStatus;
import com.shopsphere.order.entity.OrderStatusHistory;
import com.shopsphere.order.entity.PaymentMethod;
import com.shopsphere.order.repository.OrderRepository;
import com.shopsphere.order.repository.OrderStatusHistoryRepository;
import com.shopsphere.payment.entity.Payment;
import com.shopsphere.payment.entity.PaymentTransaction;
import com.shopsphere.payment.repository.PaymentRepository;
import com.shopsphere.payment.repository.PaymentTransactionRepository;

/**
 * Razorpay orchestration (test-mode compatible).
 *
 * Idempotency rules:
 *  - One payments row per order (unique FK); the gateway order id is created
 *    once and reused by repeat calls to resolveRazorpayOrder.
 *  - verifyPayment + webhook are both no-ops if the payment is already PAID.
 *
 * NOTE: called from CheckoutService inside its transaction so the reservations
 * and the payments row commit/rollback atomically with order creation.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final NotificationSaver notificationSaver;

    private final RazorpayClient razorpayClient;   // null when Razorpay disabled
    private final String razorpayKeyId;
    private final String razorpayKeySecret;
    private final String webhookSecret;
    private final boolean razorpayEnabled;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentTransactionRepository paymentTransactionRepository,
                          OrderRepository orderRepository,
                          OrderStatusHistoryRepository orderStatusHistoryRepository,
                          InventoryRepository inventoryRepository,
                          InventoryTransactionRepository inventoryTransactionRepository,
                          NotificationSaver notificationSaver,
                          @Value("${shopsphere.app.razorpay.key-id:}") String razorpayKeyId,
                          @Value("${shopsphere.app.razorpay.key-secret:}") String razorpayKeySecret,
                          @Value("${shopsphere.app.razorpay.webhook-secret:}") String webhookSecret,
                          @Value("${shopsphere.app.razorpay.enabled:false}") boolean razorpayEnabled) {
        this.paymentRepository = paymentRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.orderRepository = orderRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
        this.notificationSaver = notificationSaver;
        this.razorpayKeyId = razorpayKeyId;
        this.razorpayKeySecret = razorpayKeySecret;
        this.webhookSecret = webhookSecret;
        this.razorpayEnabled = razorpayEnabled;
        RazorpayClient client = null;
        if (razorpayEnabled && !razorpayKeyId.isBlank() && !razorpayKeySecret.isBlank()) {
            try {
                client = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to initialize Razorpay client", e);
            }
        }
        this.razorpayClient = client;
    }

    public String publishableKeyId() {
        return razorpayEnabled && !razorpayKeyId.isBlank() ? razorpayKeyId : null;
    }

    public Optional<String> gatewayOrderIdFor(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .map(Payment::getGatewayOrderId)
                .filter(s -> s != null && !s.isBlank());
    }

    /**
     * Idempotent setup for the checkout session: returns the (existing or
     * freshly created) gateway order plus the publishable key. Owner-scoped and
     * only valid while the order is still PENDING.
     */
    @Transactional
    public PaymentSetupDto createSetupForOrder(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> ResourceNotFound.of("Order"));
        if (!order.getUser().getId().equals(userId)) {
            throw ResourceNotFound.of("Order"); // never leak other users' orders
        }
        if (order.getPaymentMethod() != com.shopsphere.order.entity.PaymentMethod.RAZORPAY) {
            throw new BusinessException(400, "COD_ORDER", "This order is Cash on Delivery and needs no payment");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(409, "ORDER_NOT_PAYABLE", "Order is not awaiting payment");
        }
        resolveRazorpayOrder(order);
        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        return new PaymentSetupDto(payment.getGatewayOrderId(), publishableKeyId(),
                order.getTotalAmount(), "INR", order.getOrderNumber(), payment.getStatus().name());
    }

    /** Ownership-scoped wrapper around verifyPayment. */
    @Transactional
    public void verifyForUser(Long userId, String razorpayOrderId, String razorpayPaymentId, String signature) {
        Payment payment = paymentRepository.findByGatewayOrderId(razorpayOrderId)
                .orElseThrow(() -> ResourceNotFound.of("Payment"));
        if (!payment.getOrder().getUser().getId().equals(userId)) {
            throw ResourceNotFound.of("Payment");
        }
        verifyPayment(razorpayOrderId, razorpayPaymentId, signature);
    }

    /**
     * Resolve and persist the gateway order for this order. Idempotent — a
     * repeat call for an already-created gateway order is a no-op.
     */
    @Transactional
    public void resolveRazorpayOrder(Order order) {
        if (order.getPaymentMethod() != PaymentMethod.RAZORPAY) {
            return;
        }
        Payment payment = paymentRepository.findByOrderId(order.getId()).orElse(null);

        if (payment != null && payment.getGatewayOrderId() != null && !payment.getGatewayOrderId().isBlank()) {
            return; // already resolved — idempotent
        }
        if (!razorpayEnabled || razorpayClient == null) {
            throw new BusinessException(503, "RAZORPAY_NOT_CONFIGURED",
                    "Online payment is not configured on this environment. Please use Cash on Delivery.");
        }
        if (payment == null) {
            payment = Payment.builder()
                    .order(order)
                    .method(PaymentMethod.RAZORPAY)
                    .amount(order.getTotalAmount())
                    .status(Payment.PaymentStatus.PENDING)
                    .build();
        }
        try {
            JSONObject orderRequest = new JSONObject()
                    .put("amount", order.getTotalAmount().multiply(BigDecimal.valueOf(100)).intValue()) // paise
                    .put("currency", "INR")
                    .put("receipt", order.getOrderNumber())
                    .put("notes", new JSONObject().put("orderId", String.valueOf(order.getId())));
            com.razorpay.Order gwOrder = razorpayClient.orders.create(orderRequest);
            String gwOrderId = String.valueOf(gwOrder.get("id"));
            payment.setGatewayOrderId(gwOrderId);
            paymentRepository.save(payment);

            paymentTransactionRepository.save(PaymentTransaction.builder()
                    .payment(payment)
                    .razorpayOrderId(gwOrderId)
                    .eventType(PaymentTransaction.PaymentEvent.ORDER_CREATED)
                    .payload("amount=" + order.getTotalAmount() + ";receipt=" + order.getOrderNumber())
                    .build());
        } catch (Exception e) {
            log.error("Razorpay order creation failed for {}: {}", order.getOrderNumber(), e.getMessage());
            payment.setStatus(Payment.PaymentStatus.FAILED);
            paymentRepository.save(payment);
            throw new BusinessException(502, "PAYMENT_GATEWAY_ERROR",
                    "Gateway did not create the payment order — try again or use Cash on Delivery");
        }
    }

    /** Ownership-free read for the verify endpoint's echo of current status. */
    @Transactional(readOnly = true)
    public Payment paymentByGatewayOrderId(String gatewayOrderId) {
        return paymentRepository.findByGatewayOrderId(gatewayOrderId)
                .orElseThrow(() -> ResourceNotFound.of("Payment"));
    }

    /**
     * Endpoint verification — HMAC-SHA256 over
     * {@code <gatewayOrderId>|<razorpayPaymentId>} with the key secret
     * (Razorpay's standard checkout-handshake contract).
     */
    @Transactional
    public void verifyPayment(String razorpayOrderId, String razorpayPaymentId, String signature) {
        Payment payment = paymentRepository.findByGatewayOrderId(razorpayOrderId)
                .orElseThrow(() -> ResourceNotFound.of("Payment"));
        if (payment.getStatus() == Payment.PaymentStatus.PAID) {
            return; // idempotent
        }
        String expected = hmacSha256(razorpayOrderId + "|" + razorpayPaymentId, razorpayKeySecret);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                (signature == null ? "" : signature).getBytes(StandardCharsets.UTF_8))) {
            paymentTransactionRepository.save(PaymentTransaction.builder()
                    .payment(payment)
                    .razorpayOrderId(razorpayOrderId)
                    .razorpayPaymentId(razorpayPaymentId)
                    .signature(signature)
                    .eventType(PaymentTransaction.PaymentEvent.FAILED)
                    .payload("signature mismatch")
                    .build());
            payment.setStatus(Payment.PaymentStatus.FAILED);
            paymentRepository.save(payment);
            throw new BusinessException(400, "PAYMENT_SIGNATURE_INVALID", "Payment signature verification failed");
        }
        markPaid(payment, razorpayPaymentId, "verify endpoint");
    }

    /**
     * Razorpay webhook — HMAC-validates x-razorpay-signature over the RAW body,
     * then idempotently settles (or fails) the payment and its order.
     */
    @Transactional
    public void handleWebhook(String rawBody, String signatureHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new BusinessException(503, "WEBHOOK_NOT_CONFIGURED", "Webhook secret not configured");
        }
        String expected = hmacSha256(rawBody, webhookSecret);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                (signatureHeader == null ? "" : signatureHeader).getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(400, "WEBHOOK_SIGNATURE_INVALID", "Webhook signature mismatch");
        }

        String rzpOrderId;
        String rzpPaymentId;
        String eventType;
        try {
            JSONObject root = new JSONObject(rawBody);
            eventType = root.optString("event");
            // Razorpay shape: payload.payment.entity = { id, order_id, ... }
            JSONObject entity = root.getJSONObject("payload")
                    .getJSONObject("payment")
                    .getJSONObject("entity");
            rzpPaymentId = entity.optString("id", null);
            rzpOrderId = entity.optString("order_id", null);
        } catch (Exception e) {
            throw new BusinessException(400, "WEBHOOK_MALFORMED", "Unable to parse webhook payload");
        }
        if (rzpOrderId == null || rzpOrderId.isBlank()) {
            throw new BusinessException(400, "WEBHOOK_MALFORMED", "Webhook payload missing order_id");
        }

        Optional<Payment> paymentOpt = paymentRepository.findByGatewayOrderId(rzpOrderId);
        if (paymentOpt.isEmpty()) {
            // Not one of ours: acknowledge 200 so Razorpay stops retrying; nothing to settle.
            log.warn("Webhook for untracked gateway order {}", rzpOrderId);
            return;
        }
        Payment payment = paymentOpt.get();

        if (eventType.startsWith("payment.captured") || eventType.startsWith("order.paid")) {
            if (payment.getStatus() == Payment.PaymentStatus.PAID) {
                return; // idempotent — duplicate webhook delivery
            }
            paymentTransactionRepository.save(PaymentTransaction.builder()
                    .payment(payment)
                    .razorpayOrderId(rzpOrderId)
                    .razorpayPaymentId(rzpPaymentId)
                    .signature(signatureHeader)
                    .eventType(PaymentTransaction.PaymentEvent.WEBHOOK)
                    .payload(rawBody)
                    .build());
            markPaid(payment, rzpPaymentId, "webhook:" + eventType);
        } else if (eventType.startsWith("payment.failed")) {
            if (payment.getStatus() == Payment.PaymentStatus.PAID) {
                return; // never un-settle a paid order on a late failure event
            }
            paymentTransactionRepository.save(PaymentTransaction.builder()
                    .payment(payment)
                    .razorpayOrderId(rzpOrderId)
                    .razorpayPaymentId(rzpPaymentId)
                    .signature(signatureHeader)
                    .eventType(PaymentTransaction.PaymentEvent.WEBHOOK)
                    .payload(rawBody)
                    .build());
            releaseFailed(payment, rzpPaymentId, "webhook:" + eventType);
        }
        // Other events (refund.* handled by AdminReturnService in Phase 7) — ack silently.
    }

    /**
     * Release reserved stock + cancel order + mark payment FAILED. Any paid
     * money belongs in a refund — refunds are admin-driven (Phase 7).
     */
    @Transactional
    public void releaseFailed(Payment payment, String rzpPaymentId, String reason) {
        Order order = payment.getOrder();
        if (order.getStatus() != OrderStatus.PENDING) {
            return; // only unpaid PENDING orders may release
        }
        OrderStatus from = order.getStatus();

        order.getItems().forEach(item -> {
            int released = inventoryRepository.release(item.getProduct().getId(), item.getQuantity());
            if (released > 0) {
                inventoryRepository.findByProductId(item.getProduct().getId()).ifPresent(inv ->
                        inventoryTransactionRepository.save(com.shopsphere.inventory.entity.InventoryTransaction
                                .builder()
                                .inventory(inv)
                                .type(InventoryTransactionType.RELEASE)
                                .quantityDelta(item.getQuantity())
                                .note("Reservation released — " + reason)
                                .orderId(order.getId())
                                .build()));
            }
        });
        payment.setStatus(Payment.PaymentStatus.FAILED);
        paymentRepository.save(payment);

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason("Payment failed: " + reason);
        orderRepository.save(order);

        appendHistory(order, from, OrderStatus.CANCELLED, "system", "Payment failed — " + reason);
        notificationSaver.orderCreated(order.getUser().getId(),
                "Order " + order.getOrderNumber() + " cancelled",
                "Payment did not complete, so the order was cancelled and stock released.");
    }

    private void markPaid(Payment payment, String razorpayPaymentId, String source) {
        Order order = payment.getOrder();
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(409, "ORDER_NOT_PAYABLE", "Order is not awaiting payment");
        }
        OrderStatus from = order.getStatus();

        payment.setRazorpayPaymentId(razorpayPaymentId);
        payment.setStatus(Payment.PaymentStatus.PAID);
        payment.setPaidAt(Instant.now());
        paymentRepository.save(payment);

        // Reservation converts into a committed sale
        for (OrderItem item : order.getItems()) {
            int committed = inventoryRepository.commitReservation(item.getProduct().getId(), item.getQuantity());
            if (committed == 0) {
                // Transparent tx failure: reservation vanished under us. Transaction
                // rolls back including the PAID state — payment stays reconcilable.
                throw new BusinessException(409, "INVENTORY_CONFLICT",
                        "Stock changed while the payment was in flight. The order will be retried/refunded.");
            }
            inventoryRepository.findByProductId(item.getProduct().getId()).ifPresent(inv ->
                    inventoryTransactionRepository.save(com.shopsphere.inventory.entity.InventoryTransaction
                            .builder()
                            .inventory(inv)
                            .type(InventoryTransactionType.COMMIT)
                            .quantityDelta(-item.getQuantity())
                            .note("Stock committed — paid via " + source + " (order " + order.getOrderNumber() + ")")
                            .orderId(order.getId())
                            .build()));
        }

        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
        appendHistory(order, from, OrderStatus.CONFIRMED, "system", "Payment confirmed via " + source);

        notificationSaver.orderCreated(order.getUser().getId(),
                "Payment confirmed — " + order.getOrderNumber(),
                "₹" + order.getTotalAmount() + " paid via Razorpay.");
    }

    private void appendHistory(Order order, OrderStatus from, OrderStatus to, String actor, String note) {
        OrderStatusHistory history = OrderStatusHistory.builder()
                .previousStatus(from)
                .newStatus(to)
                .changedBy(actor)
                .note(note)
                .build();
        history.attachTo(order);
        orderStatusHistoryRepository.save(history);
    }

    private String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 failure", e);
        }
    }
}
