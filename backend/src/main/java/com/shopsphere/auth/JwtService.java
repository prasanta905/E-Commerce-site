package com.shopsphere.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HS256 access-token generator/validator. Stateless — revocation of refresh
 * tokens is handled by the RefreshTokenService.
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final String issuer;
    private final long accessTtlMs;

    public JwtService(@Value("${shopsphere.app.security.jwt.secret-key}") String secretKey,
                      @Value("${shopsphere.app.security.jwt.issuer:shopsphere}") String issuer,
                      @Value("${shopsphere.app.security.jwt.access-token-ttl-ms}") long accessTtlMs) {
        if (secretKey == null || secretKey.length() < 32) {
            throw new IllegalStateException(
                "JWT secret must be at least 32 chars — set JWT_SECRET_KEY via environment");
        }
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.accessTtlMs = accessTtlMs;
    }

    public String issueAccessToken(Long userId, String email, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .claims(Map.of(
                        "uid", userId,
                        "jti", UUID.randomUUID().toString(),
                        "roles", roles))
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessTtlMs)))
                .signWith(key)
                .compact();
    }

    /** Throws io.jsonwebtoken.JwtException on bad/expired tokens. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Optional<Long> extractUserId(Claims claims) {
        Object uid = claims.get("uid");
        if (uid instanceof Number n) {
            return Optional.of(n.longValue());
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(Claims claims) {
        Object roles = claims.get("roles");
        if (roles instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
