package com.shopsphere.auth;

import jakarta.validation.constraints.NotBlank;

/** POST /auth/forgot-password */
public record ForgotPasswordRequest(@NotBlank @jakarta.validation.constraints.Email String email) {}
