package com.shopsphere.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /auth/register */
public record RegisterRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 120, message = "Full name must be ≤ 120 characters")
        String fullName,

        @NotBlank @Email(message = "Email is not valid")
        String email,

        @NotBlank(message = "Phone is required")
        @Size(max = 20, message = "Phone must be ≤ 20 characters")
        String phone,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8–72 characters")
        String password
) {}
