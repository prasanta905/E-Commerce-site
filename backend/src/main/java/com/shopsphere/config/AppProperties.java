package com.shopsphere.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** CORS + cookie + file-upload settings sourced from env variables. */
@ConfigurationProperties(prefix = "shopsphere.app")
public record AppProperties(
        Security security,
        Cors cors
) {
    public record Security(
            Jwt jwt,
            Cookie cookie
    ) {
        record Jwt(String secretKey, String issuer, long accessTokenTtlMs,
                   long refreshTokenTtlMs) {}
        record Cookie(boolean secure, String sameSite) {}
    }
    @SuppressWarnings("unused")
    record Cors(List<String> allowedOrigins) {}
}
