package com.shopsphere.auth;

import java.util.Map;

/**
 * Payload carried by a VIEW (not modifying) body of register/login/refresh.
 */
public record AuthResponse(
        String accessToken,
        boolean emailVerified,
        String email,
        String fullName,
        String role
) {
    public static AuthResponse of(String token, boolean verified, String email, String name,
                                  java.util.List<String> authorities) {
        return new AuthResponse(token, verified, email, name,
                authorities.stream().findFirst().orElse("ROLE_USER"));
    }

    public Map<String, Object> asMap() {
        return Map.of(
                "accessToken", accessToken,
                "user", Map.of("email", email, "fullName", fullName, "role", role, "emailVerified", emailVerified)
        );
    }
}
