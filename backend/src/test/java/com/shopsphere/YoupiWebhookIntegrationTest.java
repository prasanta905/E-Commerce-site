package com.shopsphere;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopsphere.order.entity.Order;
import com.shopsphere.order.entity.OrderStatus;
import com.shopsphere.order.entity.PaymentMethod;
import com.shopsphere.order.repository.OrderRepository;
import com.shopsphere.payment.entity.Payment;
import com.shopsphere.payment.entity.PaymentProvider;
import com.shopsphere.payment.entity.YoupiEvent;
import com.shopsphere.payment.repository.PaymentRepository;
import com.shopsphere.payment.repository.YoupiEventRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * YouPI UPI webhook contract over real HTTP + MySQL.
 *
 * The authoritative rule under test: only
 * event=RECONCILED + method=UPI + settlementState=RECONCILED may confirm an
 * order. Observer alerts, COD/counter SUCCESS events, non-reconciled states,
 * bad signatures, replayed delivery ids and amount mismatches must not fulfil.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "shopsphere.app.youpi.webhook-secret=test-youpi-webhook-secret")
@ActiveProfiles("local")
class YoupiWebhookIntegrationTest {

    private static final String WEBHOOK_SECRET = "test-youpi-webhook-secret";
    private static final String WEBHOOK_PATH = "/api/v1/payments/webhook/youpi";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private YoupiEventRepository youpiEventRepository;

    // ------------------------------------------------------------- fixtures

    private record Fixture(Order order, Payment payment) {}

    @SuppressWarnings("unchecked")
    private String tokenFor(String email, String password) {
        ResponseEntity<Map> res = rest.postForEntity("/api/v1/auth/login",
                json(Map.of("email", email, "password", password), null), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = (Map<String, Object>) res.getBody().get("data");
        return (String) data.get("accessToken");
    }

    private HttpEntity<Map<String, ?>> json(Map<String, ?> body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map> res) {
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) res.getBody().get("data");
    }

    /**
     * Places a real UPI order through checkout (no gateway call — the hosted
     * session is created after checkout) and attaches the payment row the
     * webhook will settle.
     */
    @SuppressWarnings("unchecked")
    private Fixture createUpiOrder() {
        String token = tokenFor("customer2@shopsphere.local", "User@123");

        ResponseEntity<Map> catalog = rest.exchange("/api/v1/products?page=0&size=20",
                HttpMethod.GET, json(null, token), Map.class);
        List<Map<String, Object>> items = (List<Map<String, Object>>) data(catalog).get("items");
        assertThat(items).isNotEmpty();
        Map<String, Object> product = items.stream()
                .filter(p -> Boolean.TRUE.equals(p.get("inStock")))
                .findFirst()
                .orElse(items.get(0));
        long productId = ((Number) product.get("id")).longValue();

        ResponseEntity<Map> added = rest.postForEntity("/api/v1/cart/items",
                json(Map.of("productId", productId, "quantity", 1), token), Map.class);
        assertThat(added.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<Map> placed = rest.postForEntity("/api/v1/checkout",
                json(Map.of("addressId", 2, "shippingMethodId", 1, "paymentMethod", "UPI",
                        "idempotencyKey", "it-youpi-" + UUID.randomUUID(),
                        "termsAccepted", "true"), token), Map.class);
        assertThat(placed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long orderId = ((Number) ((Map<String, Object>) placed.getBody().get("data"))
                .get("orderId")).longValue();

        Order order = orderRepository.findById(orderId).orElseThrow();
        Payment payment = paymentRepository.save(Payment.builder()
                .order(order)
                .method(PaymentMethod.UPI)
                .provider(PaymentProvider.YOUPI)
                .gatewayOrderId("youpi-" + UUID.randomUUID())
                .checkoutUrl("http://localhost:7000/checkout/" + UUID.randomUUID())
                .amount(order.getTotalAmount())
                .status(Payment.PaymentStatus.PENDING)
                .build());

        return new Fixture(order, payment);
    }

    private Map<String, Object> webhookBody(Fixture fixture, String event, String method,
                                            String settlementState, String amount, String utr) {
        BigDecimal money = new BigDecimal(amount);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event", event);
        body.put("deliveryId", UUID.randomUUID().toString());
        body.put("timestamp", Instant.now().toEpochMilli());
        body.put("paymentId", fixture.payment().getGatewayOrderId());
        body.put("orderId", fixture.order().getOrderNumber());
        body.put("attemptId", fixture.payment().getGatewayOrderId());
        body.put("amount", money.setScale(2).toPlainString());
        body.put("amountPaise", money.movePointRight(2).intValueExact());
        body.put("currency", "INR");
        body.put("method", method);
        body.put("utr", utr);
        body.put("completedAt", Instant.now().toString());
        body.put("metadata", Map.of("shopsphereOrderId", String.valueOf(fixture.order().getId())));
        body.put("settlementState", settlementState);
        return body;
    }

    private ResponseEntity<String> send(Map<String, Object> body, String deliveryId, String secret)
            throws Exception {
        String raw = objectMapper.writeValueAsString(body);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Gateway-Signature", "sha256=" + hmac(raw, secret));
        headers.set("X-Gateway-Delivery-Id", deliveryId);
        return rest.exchange(WEBHOOK_PATH, HttpMethod.POST,
                new HttpEntity<>(raw, headers), String.class);
    }

    private static String hmac(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private Order reloadOrder(Fixture fixture) {
        return orderRepository.findById(fixture.order().getId()).orElseThrow();
    }

    private Payment reloadPayment(Fixture fixture) {
        return paymentRepository.findByOrderId(fixture.order().getId()).orElseThrow();
    }

    // ---------------------------------------------------------------- tests

    @Test
    void reconciledUpiSettlementConfirmsOrderAndRecordsUtr() throws Exception {
        Fixture fixture = createUpiOrder();
        String deliveryId = UUID.randomUUID().toString();
        Map<String, Object> body = webhookBody(fixture, "RECONCILED", "UPI",
                "RECONCILED", fixture.order().getTotalAmount().toPlainString(), "123456789012");

        ResponseEntity<String> res = send(body, deliveryId, WEBHOOK_SECRET);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reloadOrder(fixture).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        Payment payment = reloadPayment(fixture);
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.PAID);
        assertThat(payment.getUtr()).isEqualTo("123456789012");
        assertThat(payment.getRazorpayPaymentId()).isNull();

        YoupiEvent event = youpiEventRepository.findByDeliveryId(deliveryId).orElseThrow();
        assertThat(event.getResult()).isEqualTo(YoupiEvent.RESULT_SETTLED);
        assertThat(event.getSettlementState()).isEqualTo("RECONCILED");
    }

    @Test
    void observedBankAlertDoesNotFulfillUpiOrder() throws Exception {
        Fixture fixture = createUpiOrder();
        String deliveryId = UUID.randomUUID().toString();
        Map<String, Object> body = webhookBody(fixture, "OBSERVED_SUCCESS", "UPI",
                "NOT_RECONCILED", fixture.order().getTotalAmount().toPlainString(), "123456789012");

        ResponseEntity<String> res = send(body, deliveryId, WEBHOOK_SECRET);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reloadOrder(fixture).getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(reloadPayment(fixture).getStatus()).isEqualTo(Payment.PaymentStatus.PENDING);
        YoupiEvent event = youpiEventRepository.findByDeliveryId(deliveryId).orElseThrow();
        assertThat(event.getResult()).isEqualTo(YoupiEvent.RESULT_OBSERVED);
        assertThat(event.getReason()).contains("reconciliation");
    }

    @Test
    void successAndNonReconciledVariantsAreIgnored() throws Exception {
        Fixture fixture = createUpiOrder();
        String amount = fixture.order().getTotalAmount().toPlainString();

        Map<String, Object> success = webhookBody(fixture, "SUCCESS", "UPI",
                "NOT_BANK_SETTLEMENT", amount, "111111111111");
        Map<String, Object> reconciledEventButNotSettled = webhookBody(fixture, "RECONCILED", "UPI",
                "NOT_RECONCILED", amount, "222222222222");
        Map<String, Object> reconciledCod = webhookBody(fixture, "RECONCILED", "COD",
                "RECONCILED", amount, "333333333333");

        assertThat(send(success, UUID.randomUUID().toString(), WEBHOOK_SECRET).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(send(reconciledEventButNotSettled, UUID.randomUUID().toString(), WEBHOOK_SECRET)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(send(reconciledCod, UUID.randomUUID().toString(), WEBHOOK_SECRET).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(reloadOrder(fixture).getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(reloadPayment(fixture).getStatus()).isEqualTo(Payment.PaymentStatus.PENDING);
        assertThat(youpiEventRepository.countByOrderReference(fixture.order().getOrderNumber()))
                .isEqualTo(3);
        assertThat(youpiEventRepository.findByOrderReferenceOrderByReceivedAtAsc(
                        fixture.order().getOrderNumber()))
                .allMatch(event -> YoupiEvent.RESULT_IGNORED.equals(event.getResult()));
    }

    @Test
    void duplicateDeliveryIsProcessedOnlyOnce() throws Exception {
        Fixture fixture = createUpiOrder();
        String deliveryId = UUID.randomUUID().toString();
        Map<String, Object> body = webhookBody(fixture, "RECONCILED", "UPI",
                "RECONCILED", fixture.order().getTotalAmount().toPlainString(), "444444444444");

        assertThat(send(body, deliveryId, WEBHOOK_SECRET).getStatusCode()).isEqualTo(HttpStatus.OK);
        Payment afterFirst = reloadPayment(fixture);
        assertThat(afterFirst.getStatus()).isEqualTo(Payment.PaymentStatus.PAID);
        Instant firstPaidAt = afterFirst.getPaidAt();
        assertThat(firstPaidAt).isNotNull();

        assertThat(send(body, deliveryId, WEBHOOK_SECRET).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(youpiEventRepository.countByDeliveryId(deliveryId)).isEqualTo(1);
        Payment afterReplay = reloadPayment(fixture);
        assertThat(afterReplay.getStatus()).isEqualTo(Payment.PaymentStatus.PAID);
        assertThat(afterReplay.getPaidAt()).isEqualTo(firstPaidAt);
        assertThat(reloadOrder(fixture).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void invalidSignatureIsRejectedBeforeAnyAuditRow() throws Exception {
        Fixture fixture = createUpiOrder();
        String deliveryId = UUID.randomUUID().toString();
        Map<String, Object> body = webhookBody(fixture, "RECONCILED", "UPI",
                "RECONCILED", fixture.order().getTotalAmount().toPlainString(), "555555555555");

        ResponseEntity<String> res = send(body, deliveryId, "wrong-webhook-secret");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reloadOrder(fixture).getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(youpiEventRepository.countByDeliveryId(deliveryId)).isZero();
    }

    @Test
    void amountMismatchIsRejected() throws Exception {
        Fixture fixture = createUpiOrder();
        String tampered = fixture.order().getTotalAmount().add(new BigDecimal("1.00")).toPlainString();
        String deliveryId = UUID.randomUUID().toString();
        Map<String, Object> body = webhookBody(fixture, "RECONCILED", "UPI",
                "RECONCILED", tampered, "666666666666");

        ResponseEntity<String> res = send(body, deliveryId, WEBHOOK_SECRET);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reloadOrder(fixture).getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(reloadPayment(fixture).getStatus()).isEqualTo(Payment.PaymentStatus.PENDING);
        YoupiEvent event = youpiEventRepository.findByDeliveryId(deliveryId).orElseThrow();
        assertThat(event.getResult()).isEqualTo(YoupiEvent.RESULT_REJECTED);
        assertThat(event.getReason()).contains("amount");
    }

    @Test
    void lateReconciledWebhookForEarlierAttemptConfirmsOrder() throws Exception {
        Fixture fixture = createUpiOrder();
        String earlierAttemptId = fixture.payment().getGatewayOrderId();
        String currentAttemptId = "youpi-" + UUID.randomUUID();

        // Simulate a resumed checkout: the payment row now points at a newer
        // attempt, while the earlier session is still audited.
        Payment payment = fixture.payment();
        payment.setGatewayOrderId(currentAttemptId);
        paymentRepository.save(payment);
        youpiEventRepository.save(YoupiEvent.builder()
                .payment(payment)
                .eventType(YoupiEvent.TYPE_SESSION_CREATED)
                .result(YoupiEvent.RESULT_SESSION_CREATED)
                .orderReference(fixture.order().getOrderNumber())
                .providerPaymentId(earlierAttemptId)
                .amount(fixture.order().getTotalAmount())
                .build());

        String deliveryId = UUID.randomUUID().toString();
        Map<String, Object> body = webhookBody(fixture, "RECONCILED", "UPI", "RECONCILED",
                fixture.order().getTotalAmount().toPlainString(), "888888888888");
        body.put("paymentId", earlierAttemptId);

        assertThat(send(body, deliveryId, WEBHOOK_SECRET).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(reloadOrder(fixture).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(reloadPayment(fixture).getStatus()).isEqualTo(Payment.PaymentStatus.PAID);
        YoupiEvent event = youpiEventRepository.findByDeliveryId(deliveryId).orElseThrow();
        assertThat(event.getResult()).isEqualTo(YoupiEvent.RESULT_SETTLED);
        assertThat(event.getProviderPaymentId()).isEqualTo(earlierAttemptId);
    }

    @Test
    void additionalSettlementForAlreadyPaidOrderIsFlaggedForManualReview() throws Exception {
        Fixture fixture = createUpiOrder();
        String amount = fixture.order().getTotalAmount().toPlainString();

        String firstDelivery = UUID.randomUUID().toString();
        Map<String, Object> first = webhookBody(fixture, "RECONCILED", "UPI",
                "RECONCILED", amount, "123123123123");
        assertThat(send(first, firstDelivery, WEBHOOK_SECRET).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reloadOrder(fixture).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(reloadPayment(fixture).getUtr()).isEqualTo("123123123123");

        // A distinct delivery claiming a second settlement must not change the
        // order again; the extra money is surfaced for a manual refund review.
        String secondDelivery = UUID.randomUUID().toString();
        Map<String, Object> second = webhookBody(fixture, "RECONCILED", "UPI",
                "RECONCILED", amount, "999999999999");
        assertThat(send(second, secondDelivery, WEBHOOK_SECRET).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(reloadOrder(fixture).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(reloadPayment(fixture).getUtr()).isEqualTo("123123123123");
        YoupiEvent event = youpiEventRepository.findByDeliveryId(secondDelivery).orElseThrow();
        assertThat(event.getResult()).isEqualTo(YoupiEvent.RESULT_REJECTED);
        assertThat(event.getReason()).contains("manual refund review");
    }

    @Test
    void replayedDeliveryIdIsRejectedByUniqueAuditKey() throws Exception {
        Fixture fixture = createUpiOrder();
        String amount = fixture.order().getTotalAmount().toPlainString();
        String deliveryId = UUID.randomUUID().toString();

        // First delivery is an alert, recorded but not fulfilled.
        assertThat(send(webhookBody(fixture, "OBSERVED_SUCCESS", "UPI", "NOT_RECONCILED",
                amount, "777777777777"), deliveryId, WEBHOOK_SECRET).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(reloadOrder(fixture).getStatus()).isEqualTo(OrderStatus.PENDING);

        // A replay claiming RECONCILED must NOT be processed under the same id.
        assertThat(send(webhookBody(fixture, "RECONCILED", "UPI", "RECONCILED",
                amount, "777777777777"), deliveryId, WEBHOOK_SECRET).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(youpiEventRepository.countByDeliveryId(deliveryId)).isEqualTo(1);
        assertThat(reloadOrder(fixture).getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(reloadPayment(fixture).getStatus()).isEqualTo(Payment.PaymentStatus.PENDING);
    }
}
