package com.shopsphere.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** POST /auth/login */
public record LoginRequest(
        @NotBlank @Email(message = "Email is not valid") String email,
        @NotBlank(message = "Password is required") String password
) {}
