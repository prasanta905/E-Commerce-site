package com.shopsphere.checkout;

import com.shopsphere.common.ApiResponse;
import com.shopsphere.security.CurrentUser;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Checkout", description = "Cart checkout: preview totals and place an order")
@RestController
@RequestMapping("/api/v1/checkout")
public class CheckoutController {

    private final CheckoutService checkoutService;
    private final CurrentUser currentUser;

    public CheckoutController(CheckoutService checkoutService, CurrentUser currentUser) {
        this.checkoutService = checkoutService;
        this.currentUser = currentUser;
    }

    public record PreviewRequest(
            @NotNull Long addressId,
            @NotNull Long shippingMethodId,
            String couponCode) {}

    public record PlaceOrderRequest(
            @NotNull Long addressId,
            @NotNull Long shippingMethodId,
            @NotBlank String paymentMethod, // "RAZORPAY" | "COD"
            String couponCode,
            @NotEmpty String idempotencyKey,
            @NotEmpty String termsAccepted) {}

    @Operation(summary = "Available shipping methods (code-defined catalogue; free above thresholds)")
    @GetMapping("/shipping-methods")
    public ResponseEntity<ApiResponse<List<ShippingMethod>>> shippingMethods() {
        return ResponseEntity.ok(ApiResponse.ok(CheckoutService.SHIPPING_METHODS));
    }

    @Operation(summary = "Preview totals (server-computed subtotal/discount/shipping) with stock warnings")
    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<CheckoutPreviewDto>> preview(@Valid @RequestBody PreviewRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(checkoutService.preview(
                currentUser.requireUserId(), request.addressId(),
                request.shippingMethodId(), request.couponCode())));
    }

    @Operation(summary = "Place an order from the cart (reserves stock atomically; returns gateway order id for Razorpay)")
    @PostMapping
    public ResponseEntity<ApiResponse<CheckoutResponse>> place(@Valid @RequestBody PlaceOrderRequest request) {
        if (!"true".equalsIgnoreCase(request.termsAccepted())) {
            throw com.shopsphere.common.exception.BusinessException.badRequest(
                    "TERMS_REQUIRED", "Accepting the terms of service is required to place an order");
        }
        CheckoutRequest serviceRequest = new CheckoutRequest(request.addressId(), request.shippingMethodId(),
                request.couponCode(), request.paymentMethod(), request.idempotencyKey());
        CheckoutResponse response = checkoutService.checkout(currentUser.requireUserId(), serviceRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }
}
