package com.shopsphere.payment;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.shopsphere.common.ApiResponse;
import com.shopsphere.common.exception.BusinessException;
import com.shopsphere.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payments", description = "Razorpay order setup, checkout verification, webhook settlement")
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final CurrentUser currentUser;

    public PaymentController(PaymentService paymentService, CurrentUser currentUser) {
        this.paymentService = paymentService;
        this.currentUser = currentUser;
    }

    /** Frontend response after the checkout modal is dismissed. */
    public record VerifyRequest(
            @NotBlank String razorpayOrderId,
            @NotBlank String razorpayPaymentId,
            @NotBlank String razorpaySignature) {}

    public record VerifyResponse(String orderStatus, String paymentStatus) {}

    @Operation(summary = "Resolve (or reuse) the Razorpay gateway order for a PENDING order")
    @PostMapping("/orders/{orderId}/create")
    public ResponseEntity<ApiResponse<PaymentSetupDto>> createOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.ok(
                paymentService.createSetupForOrder(orderId, currentUser.requireUserId())));
    }

    @Operation(summary = "Verify the checkout handshake (HMAC of gatewayOrderId|paymentId)")
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<VerifyResponse>> verify(@jakarta.validation.Valid @RequestBody VerifyRequest request) {
        paymentService.verifyPayment(request.razorpayOrderId(), request.razorpayPaymentId(),
                request.razorpaySignature());
        // Idempotent — repeated verify calls for an already-paid payment are no-ops.
        var payment = paymentService.paymentByGatewayOrderId(request.razorpayOrderId());
        return ResponseEntity.ok(ApiResponse.ok(
                new VerifyResponse("CONFIRMED", payment.getStatus().name())));
    }

    @Operation(summary = "Razorpay webhook (server-to-server; raw body must be HMAC-verified)")
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(HttpServletRequest request) {
        String rawBody = readRawBody(request);
        String signature = request.getHeader("X-Razorpay-Signature");
        paymentService.handleWebhook(rawBody, signature);
        return ResponseEntity.ok().build();
    }

    private String readRawBody(HttpServletRequest request) {
        try (InputStream in = request.getInputStream()) {
            byte[] bytes = in.readAllBytes();
            if (bytes.length == 0) {
                throw BusinessException.badRequest("WEBHOOK_EMPTY", "Empty webhook payload");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw BusinessException.badRequest("WEBHOOK_UNREADABLE", "Unable to read webhook payload");
        }
    }
}
