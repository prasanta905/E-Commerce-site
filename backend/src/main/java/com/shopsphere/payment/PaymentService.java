package com.shopsphere.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.razorpay.RazorpayClient;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

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
import com.shopsphere.payment.entity.PaymentProvider;
import com.shopsphere.payment.entity.PaymentTransaction;
import com.shopsphere.payment.entity.YoupiEvent;
import com.shopsphere.payment.repository.PaymentRepository;
import com.shopsphere.payment.repository.PaymentTransactionRepository;
import com.shopsphere.payment.repository.YoupiEventRepository;

/**
 * Payment orchestration for Razorpay and the self-hosted YouPI PG UPI gateway.
 *
 * Idempotency rules:
 *  - One payments row per order (unique FK); the gateway session id is created
 *    once and reused/refreshed by repeat calls.
 *  - Razorpay verify + webhook are no-ops if the payment is already PAID.
 *  - YouPI webhooks are deduplicated by X-Gateway-Delivery-Id, and a UPI order
 *    is fulfilled ONLY by event=RECONCILED + method=UPI +
 *    settlementState=RECONCILED. Browser redirects and OBSERVED_SUCCESS never
 *    confirm an order.
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
    private final YoupiEventRepository youpiEventRepository;
    private final ObjectMapper objectMapper;

    private final RazorpayClient razorpayClient;   // null when Razorpay disabled
    private final String razorpayKeyId;
    private final String razorpayKeySecret;
    private final String webhookSecret;
    private final boolean razorpayEnabled;

    private final RestClient youpiClient;
    private final String youpiMerchantId;
    private final String youpiMerchantKey;
    private final String youpiWebhookSecret;
    private final String youpiReturnBaseUrl;
    private final boolean youpiEnabled;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentTransactionRepository paymentTransactionRepository,
                          OrderRepository orderRepository,
                          OrderStatusHistoryRepository orderStatusHistoryRepository,
                          InventoryRepository inventoryRepository,
                          InventoryTransactionRepository inventoryTransactionRepository,
                          NotificationSaver notificationSaver,
                          YoupiEventRepository youpiEventRepository,
                          ObjectMapper objectMapper,
                          @Value("${shopsphere.app.razorpay.key-id:}") String razorpayKeyId,
                          @Value("${shopsphere.app.razorpay.key-secret:}") String razorpayKeySecret,
                          @Value("${shopsphere.app.razorpay.webhook-secret:}") String webhookSecret,
                          @Value("${shopsphere.app.razorpay.enabled:false}") boolean razorpayEnabled,
                          @Value("${shopsphere.app.youpi.base-url:http://localhost:6100}") String youpiBaseUrl,
                          @Value("${shopsphere.app.youpi.merchant-id:}") String youpiMerchantId,
                          @Value("${shopsphere.app.youpi.merchant-key:}") String youpiMerchantKey,
                          @Value("${shopsphere.app.youpi.webhook-secret:}") String youpiWebhookSecret,
                          @Value("${shopsphere.app.youpi.return-base-url:http://localhost:5173}") String youpiReturnBaseUrl,
                          @Value("${shopsphere.app.youpi.enabled:false}") boolean youpiEnabled) {
        this.paymentRepository = paymentRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.orderRepository = orderRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
        this.notificationSaver = notificationSaver;
        this.youpiEventRepository = youpiEventRepository;
        this.objectMapper = objectMapper;
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

        this.youpiMerchantId = youpiMerchantId == null ? "" : youpiMerchantId.trim();
        this.youpiMerchantKey = youpiMerchantKey == null ? "" : youpiMerchantKey.trim();
        this.youpiWebhookSecret = youpiWebhookSecret == null ? "" : youpiWebhookSecret.trim();
        this.youpiReturnBaseUrl = stripTrailingSlash(youpiReturnBaseUrl);
        this.youpiEnabled = youpiEnabled;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(15_000);
        String base = stripTrailingSlash(youpiBaseUrl);
        this.youpiClient = RestClient.builder()
                .baseUrl(base.isBlank() ? "http://localhost:6100" : base)
                .requestFactory(requestFactory)
                .build();
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
            throw new BusinessException(400, "NOT_RAZORPAY_ORDER",
                    "This order does not use Razorpay");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(409, "ORDER_NOT_PAYABLE", "Order is not awaiting payment");
        }
        resolveRazorpayOrder(order);
        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        return new PaymentSetupDto(payment.getGatewayOrderId(), publishableKeyId(),
                order.getTotalAmount(), "INR", order.getOrderNumber(), payment.getStatus().name());
    }

    // ------------------------------------------------------------ YouPI UPI

    /**
     * Create (or resume) a hosted YouPI checkout for a PENDING UPI order.
     *
     * The amount is always the server-computed order total. YouPI is called
     * server-side with the merchant key; only the returned checkoutUrl reaches
     * the browser. A successful checkout redirect is not proof of payment —
     * fulfillment is exclusively webhook-driven.
     */
    @Transactional
    public YoupiSessionDto createYoupiSession(Long orderId, Long userId) {
        if (!youpiEnabled || youpiMerchantId.isBlank() || youpiMerchantKey.isBlank()
                || youpiWebhookSecret.isBlank()) {
            // Without a webhook secret a session could never settle, so refuse
            // to create one rather than take money that cannot be confirmed.
            throw new BusinessException(503, "YOUPI_NOT_CONFIGURED",
                    "UPI payment is not configured on this environment. Please use Cash on Delivery.");
        }
        if (youpiReturnBaseUrl.isBlank()) {
            throw new BusinessException(503, "YOUPI_NOT_CONFIGURED",
                    "UPI return URL is not configured on this environment.");
        }

        Order order = orderRepository.findById(orderId).orElseThrow(() -> ResourceNotFound.of("Order"));
        if (!order.getUser().getId().equals(userId)) {
            throw ResourceNotFound.of("Order"); // never leak other users' orders
        }
        if (order.getPaymentMethod() != PaymentMethod.UPI) {
            throw new BusinessException(400, "NOT_UPI_ORDER", "This order is not a UPI order");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(409, "ORDER_NOT_PAYABLE", "Order is not awaiting payment");
        }

        String returnUrl = youpiReturnBaseUrl + "/orders/" + order.getId() + "?payment=youpi";

        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("name", order.getUser().getFullName());
        customer.put("email", order.getUser().getEmail());
        if (order.getUser().getPhone() != null && !order.getUser().getPhone().isBlank()) {
            customer.put("phoneNumber", order.getUser().getPhone());
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("shopsphereOrderId", String.valueOf(order.getId()));
        metadata.put("orderNumber", order.getOrderNumber());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getOrderNumber());
        payload.put("amount", order.getTotalAmount().setScale(2, RoundingMode.HALF_UP).toPlainString());
        payload.put("currency", "INR");
        payload.put("method", "UPI");
        payload.put("returnUrl", returnUrl);
        payload.put("customer", customer);
        payload.put("metadata", metadata);

        YoupiCreateResponse response;
        try {
            response = youpiClient.post()
                    .uri("/api/v1/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Merchant-Id", youpiMerchantId)
                    .header("X-Merchant-Key", youpiMerchantKey)
                    .header("Idempotency-Key", order.getOrderNumber())
                    .body(payload)
                    .retrieve()
                    .body(YoupiCreateResponse.class);
        } catch (RestClientResponseException e) {
            String gatewayCode = youpiErrorCode(e.getResponseBodyAsString());
            log.error("YouPI session creation failed for {} (HTTP {}): {}",
                    order.getOrderNumber(), e.getStatusCode().value(), e.getResponseBodyAsString());
            if ("ORDER_AMOUNT_MISMATCH".equals(gatewayCode)) {
                throw new BusinessException(409, "PAYMENT_AMOUNT_MISMATCH",
                        "The payment gateway already has a different amount for this order. Contact support.");
            }
            throw new BusinessException(502, "YOUPI_GATEWAY_ERROR",
                    "The UPI gateway did not create the payment session. Try again or use Cash on Delivery.");
        } catch (Exception e) {
            log.error("YouPI session creation failed for {}: {}", order.getOrderNumber(), e.getMessage());
            throw new BusinessException(502, "YOUPI_GATEWAY_ERROR",
                    "The UPI gateway is unreachable. Try again or use Cash on Delivery.");
        }

        if (response == null || isBlank(response.paymentId()) || isBlank(response.checkoutUrl())) {
            log.error("YouPI returned an incomplete session for {}: {}", order.getOrderNumber(), response);
            throw new BusinessException(502, "YOUPI_GATEWAY_ERROR",
                    "The UPI gateway returned an incomplete payment session. Try again.");
        }
        // Never redirect the browser to a non-HTTP URL, and refuse a session
        // that does not echo the server-computed money values.
        String checkoutUrl = response.checkoutUrl();
        if (!(checkoutUrl.startsWith("https://") || checkoutUrl.startsWith("http://"))) {
            log.error("YouPI returned an unsafe checkout URL for {}: {}", order.getOrderNumber(), checkoutUrl);
            throw new BusinessException(502, "YOUPI_GATEWAY_ERROR",
                    "The UPI gateway returned an invalid checkout URL. Try again.");
        }
        if (response.amount() != null && !response.amount().isBlank()) {
            BigDecimal gatewayAmount;
            try {
                gatewayAmount = new BigDecimal(response.amount());
            } catch (NumberFormatException e) {
                gatewayAmount = null;
            }
            if (gatewayAmount == null || gatewayAmount.compareTo(order.getTotalAmount()) != 0) {
                log.error("YouPI returned amount {} for order {} (expected {})",
                        response.amount(), order.getOrderNumber(), order.getTotalAmount());
                throw new BusinessException(502, "YOUPI_GATEWAY_ERROR",
                        "The UPI gateway returned a mismatched amount. Try again.");
            }
        }
        if (response.currency() != null && !response.currency().isBlank()
                && !"INR".equalsIgnoreCase(response.currency())) {
            log.error("YouPI returned currency {} for order {}", response.currency(), order.getOrderNumber());
            throw new BusinessException(502, "YOUPI_GATEWAY_ERROR",
                    "The UPI gateway returned an unsupported currency. Try again.");
        }
        if (response.orderId() != null && !response.orderId().isBlank()
                && !order.getOrderNumber().equals(response.orderId())) {
            log.error("YouPI returned orderId {} for order {}", response.orderId(), order.getOrderNumber());
            throw new BusinessException(502, "YOUPI_GATEWAY_ERROR",
                    "The UPI gateway returned a session for a different order. Try again.");
        }

        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        if (payment == null) {
            payment = Payment.builder()
                    .order(order)
                    .amount(order.getTotalAmount())
                    .status(Payment.PaymentStatus.PENDING)
                    .build();
        }
        if (payment.getStatus() == Payment.PaymentStatus.PAID) {
            return new YoupiSessionDto(response.paymentId(), response.checkoutUrl(),
                    order.getTotalAmount(), "INR", order.getOrderNumber(),
                    payment.getStatus().name(), response.status());
        }

        payment.setProvider(PaymentProvider.YOUPI);
        payment.setMethod(PaymentMethod.UPI);
        payment.setAmount(order.getTotalAmount());
        payment.setGatewayOrderId(response.paymentId());
        payment.setCheckoutUrl(response.checkoutUrl());
        payment.setStatus(Payment.PaymentStatus.PENDING);
        paymentRepository.save(payment);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("request", payload);
        audit.put("response", response);
        youpiEventRepository.save(YoupiEvent.builder()
                .payment(payment)
                .eventType(YoupiEvent.TYPE_SESSION_CREATED)
                .result(YoupiEvent.RESULT_SESSION_CREATED)
                .orderReference(order.getOrderNumber())
                .providerPaymentId(response.paymentId())
                .amount(order.getTotalAmount())
                .payload(writeJson(audit))
                .build());

        return new YoupiSessionDto(response.paymentId(), response.checkoutUrl(),
                order.getTotalAmount(), "INR", order.getOrderNumber(),
                payment.getStatus().name(), response.status());
    }

    /**
     * YouPI webhook — verifies the HMAC over the RAW body, deduplicates by
     * delivery id, cross-checks the signed payload against the stored order,
     * and settles ONLY on event=RECONCILED + method=UPI +
     * settlementState=RECONCILED.
     */
    @Transactional
    public void handleYoupiWebhook(String rawBody, String signatureHeader, String deliveryId) {
        if (youpiWebhookSecret.isBlank()) {
            throw new BusinessException(503, "YOUPI_WEBHOOK_NOT_CONFIGURED",
                    "YouPI webhook secret is not configured");
        }
        verifyYoupiSignature(rawBody, signatureHeader);
        if (isBlank(deliveryId)) {
            throw BusinessException.badRequest("YOUPI_DELIVERY_ID_MISSING",
                    "X-Gateway-Delivery-Id is required");
        }
        if (deliveryId.length() > 64) {
            throw BusinessException.badRequest("YOUPI_DELIVERY_ID_INVALID",
                    "X-Gateway-Delivery-Id is too long");
        }
        if (youpiEventRepository.existsByDeliveryId(deliveryId)) {
            log.info("Duplicate YouPI delivery {} acknowledged without reprocessing", deliveryId);
            return;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            throw BusinessException.badRequest("YOUPI_WEBHOOK_MALFORMED", "Unable to parse YouPI webhook payload");
        }

        String event = text(root, "event");
        String method = text(root, "method");
        String settlementState = text(root, "settlementState");
        String orderReference = text(root, "orderId");
        String providerPaymentId = text(root, "paymentId");
        String utr = text(root, "utr");
        String currency = text(root, "currency");
        BigDecimal amount = decimal(root, "amount");
        if (event == null || orderReference == null || providerPaymentId == null || amount == null) {
            throw BusinessException.badRequest("YOUPI_WEBHOOK_MALFORMED",
                    "Webhook payload is missing event/orderId/paymentId/amount");
        }

        Payment payment = paymentRepository.findByGatewayOrderId(providerPaymentId)
                .or(() -> orderRepository.findByOrderNumber(orderReference)
                        .flatMap(o -> paymentRepository.findByOrderId(o.getId())))
                .orElse(null);

        YoupiEvent.YoupiEventBuilder audit = YoupiEvent.builder()
                .deliveryId(deliveryId)
                .payment(payment)
                .eventType(YoupiEvent.TYPE_WEBHOOK)
                .event(event)
                .method(method)
                .settlementState(settlementState)
                .orderReference(orderReference)
                .providerPaymentId(providerPaymentId)
                .amount(amount)
                .utr(utr)
                .payload(rawBody);

        if (payment == null) {
            rejectYoupiEvent(audit, "Unknown YouPI payment or order reference");
            return;
        }
        if (payment.getProvider() != PaymentProvider.YOUPI
                || payment.getOrder().getPaymentMethod() != PaymentMethod.UPI) {
            rejectYoupiEvent(audit, "Payment row is not a YouPI UPI payment");
            return;
        }
        if (!payment.getOrder().getOrderNumber().equals(orderReference)) {
            rejectYoupiEvent(audit, "orderId does not match the stored order");
            return;
        }
        if (payment.getAmount().compareTo(amount) != 0) {
            rejectYoupiEvent(audit, "amount does not match the stored order total");
            return;
        }
        if (currency != null && !"INR".equalsIgnoreCase(currency)) {
            rejectYoupiEvent(audit, "currency is not INR");
            return;
        }
        JsonNode amountPaise = root.get("amountPaise");
        if (amountPaise != null && !amountPaise.isNull()) {
            long expectedPaise;
            try {
                expectedPaise = amount.movePointRight(2).longValueExact();
            } catch (ArithmeticException e) {
                rejectYoupiEvent(audit, "amount has more than two decimal places");
                return;
            }
            if (!amountPaise.canConvertToLong() || amountPaise.asLong() != expectedPaise) {
                rejectYoupiEvent(audit, "amountPaise does not match amount");
                return;
            }
        }

        if (isBlank(payment.getGatewayOrderId())) {
            // The signed webhook is authoritative; adopt the id if session
            // persistence raced the first delivery.
            payment.setGatewayOrderId(providerPaymentId);
            paymentRepository.save(payment);
        } else if (!payment.getGatewayOrderId().equals(providerPaymentId)
                && !youpiEventRepository.existsByOrderReferenceAndProviderPaymentIdAndEventType(
                        orderReference, providerPaymentId, YoupiEvent.TYPE_SESSION_CREATED)) {
            // A resumed checkout may have several YouPI attempts for one order.
            // Accept a reconciled settlement for any attempt we actually created
            // (audited as SESSION_CREATED); reject unknown payment ids.
            rejectYoupiEvent(audit, "paymentId does not match the stored gateway session");
            return;
        }

        boolean reconciledUpiSettlement = "RECONCILED".equals(event)
                && "UPI".equalsIgnoreCase(method)
                && "RECONCILED".equals(settlementState);

        if (!reconciledUpiSettlement) {
            String reason = switch (event) {
                case "OBSERVED_SUCCESS" -> "Bank alert observed; waiting for statement reconciliation";
                case "MANUAL_REVIEW" -> "Gateway flagged the payment for manual review";
                case "SUCCESS" -> "SUCCESS is COD/counter only; not a UPI bank settlement";
                case "FAILED", "EXPIRED", "CANCELLED" ->
                        "Gateway reported " + event + "; order remains payable";
                case "RECONCILED" ->
                        "RECONCILED event lacked method=UPI and settlementState=RECONCILED";
                default -> "Event does not authorize UPI fulfillment";
            };
            boolean observed = "OBSERVED_SUCCESS".equals(event) || "MANUAL_REVIEW".equals(event);
            youpiEventRepository.save(audit
                    .result(observed ? YoupiEvent.RESULT_OBSERVED : YoupiEvent.RESULT_IGNORED)
                    .reason(reason)
                    .build());
            log.info("YouPI webhook {} for {} recorded without fulfillment: {}", event, orderReference, reason);
            return;
        }

        if (payment.getStatus() == Payment.PaymentStatus.PAID) {
            // A different delivery id for an already-paid order is not an
            // idempotent retry: the customer may have paid twice. Keep the
            // first UTR and flag the extra money for a manual refund review.
            boolean sameUtr = utr != null && utr.equals(payment.getUtr());
            youpiEventRepository.save(audit
                    .result(sameUtr ? YoupiEvent.RESULT_IGNORED : YoupiEvent.RESULT_REJECTED)
                    .reason(sameUtr
                            ? "Duplicate reconciled delivery for an already-paid payment"
                            : "Order already paid; additional UPI settlement requires manual refund review")
                    .build());
            if (!sameUtr) {
                log.warn("YouPI reported an additional settled UPI payment ({}) for already-paid order {}; "
                        + "manual refund review required", providerPaymentId, orderReference);
            }
            return;
        }
        if (payment.getOrder().getStatus() != OrderStatus.PENDING) {
            rejectYoupiEvent(audit, "Order is not awaiting payment");
            return;
        }

        if (utr != null) {
            payment.setUtr(utr);
        } else {
            log.warn("Reconciled UPI settlement for {} has no UTR in the webhook; "
                    + "the audit row still records the signed payload", orderReference);
        }
        youpiEventRepository.save(audit.result(YoupiEvent.RESULT_SETTLED)
                .reason("Reconciled UPI bank settlement")
                .build());
        markPaid(payment, providerPaymentId, "YouPI reconciled UPI settlement");
    }

    private void rejectYoupiEvent(YoupiEvent.YoupiEventBuilder audit, String reason) {
        youpiEventRepository.save(audit
                .result(YoupiEvent.RESULT_REJECTED)
                .reason(reason.length() > 255 ? reason.substring(0, 255) : reason)
                .build());
        log.warn("YouPI webhook rejected: {}", reason);
    }

    private void verifyYoupiSignature(String rawBody, String signatureHeader) {
        String provided = signatureHeader == null ? "" : signatureHeader.trim();
        if (provided.regionMatches(true, 0, "sha256=", 0, 7)) {
            provided = provided.substring(7);
        }
        String expected = hmacSha256(rawBody, youpiWebhookSecret);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(400, "YOUPI_WEBHOOK_SIGNATURE_INVALID",
                    "YouPI webhook signature verification failed");
        }
    }

    private String youpiErrorCode(String responseBody) {
        try {
            JsonNode node = objectMapper.readTree(responseBody);
            String code = node.path("code").asText(null);
            return code != null ? code : node.path("error").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private String text(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private BigDecimal decimal(JsonNode root, String field) {
        String text = text(root, field);
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{\"serializationError\":true}";
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String stripTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /** Shape of the YouPI create-session response (extra fields ignored). */
    record YoupiCreateResponse(String paymentId, String orderId, String amount,
                               String currency, String status, String checkoutUrl) {}

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
        if (payment.getProvider() != PaymentProvider.RAZORPAY) {
            throw new BusinessException(400, "PAYMENT_PROVIDER_MISMATCH", "Payment does not belong to Razorpay");
        }
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
        if (payment.getProvider() != PaymentProvider.RAZORPAY) {
            log.warn("Razorpay webhook targeted a non-Razorpay payment for gateway order {}", rzpOrderId);
            return;
        }

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
        if (payment.getProvider() != PaymentProvider.RAZORPAY) {
            return; // UPI orders stay payable; YouPI may create a new attempt
        }
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

        if (payment.getProvider() == PaymentProvider.YOUPI) {
            // The UTR is recorded from the reconciled webhook before this call.
            // Never store a YouPI payment id in the Razorpay-specific column.
        } else {
            payment.setRazorpayPaymentId(razorpayPaymentId);
        }
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

        String paidVia = payment.getProvider() == PaymentProvider.YOUPI ? "YouPI UPI" : "Razorpay";
        notificationSaver.orderCreated(order.getUser().getId(),
                "Payment confirmed — " + order.getOrderNumber(),
                "₹" + order.getTotalAmount() + " paid via " + paidVia + ".");
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
