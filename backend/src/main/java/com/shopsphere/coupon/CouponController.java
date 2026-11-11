package com.shopsphere.coupon;

import java.math.BigDecimal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shopsphere.common.ApiResponse;
import com.shopsphere.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Coupons", description = "Coupon validation & discounts")
@RestController
@RequestMapping("/api/v1/coupons")
public class CouponController {

    private final CouponService couponService;
    private final CurrentUser currentUser;

    public CouponController(CouponService couponService, CurrentUser currentUser) {
        this.couponService = couponService;
        this.currentUser = currentUser;
    }

    @Operation(summary = "Validate and preview coupon discount against cart/checkout subtotal")
    @GetMapping("/apply")
    public ResponseEntity<ApiResponse<CouponApplication>> apply(
            @RequestParam String code,
            @RequestParam BigDecimal subtotal) {
        Long userId = null;
        try {
            userId = currentUser.requireUserId();
        } catch (Exception ignored) {
            // Anonymous guest coupon check allowed
        }
        CouponApplication result = couponService.apply(code, subtotal, userId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
