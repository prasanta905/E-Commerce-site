package com.shopsphere.auth;

import com.shopsphere.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication", description = "Customer + admin authentication")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Register a customer account; returns access token + sets refresh cookie")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        AuthResponse auth = authService.register(request, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(auth));
    }

    @Operation(summary = "Login with email+password")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request, response)));
    }

    @Operation(summary = "Rotate refresh token; returns new access token (rotation enforced)")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(ApiResponse.ok(
                authService.refresh(extractRefreshJti(request), response)));
    }

    @Operation(summary = "Logout — revokes the refresh-token family")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(extractRefreshJti(request), response);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Request a password-reset link (no account enumeration)")
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Complete password reset with a one-time token")
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Verify email using the emailed token")
    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request);
        return ResponseEntity.ok().build();
    }

    private String extractRefreshJti(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if ("SS_REFRESH".equals(c.getName())) return c.getValue();
        }
        return null;
    }
}
