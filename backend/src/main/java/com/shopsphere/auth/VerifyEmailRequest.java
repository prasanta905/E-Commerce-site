package com.shopsphere.auth;

import jakarta.validation.constraints.NotBlank;

/** POST /auth/verify-email */
public record VerifyEmailRequest(@NotBlank(message = "Token required") String token) {}
