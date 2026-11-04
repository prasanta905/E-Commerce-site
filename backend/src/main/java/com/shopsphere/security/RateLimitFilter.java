package com.shopsphere.security;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Simple in-memory token bucket for sensitive endpoints (login / register /
 * forgot-password / payment verify). Protects against trivial brute force in
 * single-node deploys — production multi-node should swap a shared store.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int LIMIT = 20;                 // requests per minute
    private static final long WINDOW_MS = 60_000;

    private record Bucket(long windowStart, int count) {}

    private final Map<String, Bucket> hits = new ConcurrentHashMap<>();

    private static final Map<String, Boolean> PROTECTED = Map.of(
            "/api/v1/auth/login", true,
            "/api/v1/auth/register", true,
            "/api/v1/auth/forgot-password", true,
            "/api/v1/payments/verify", true,
            "/api/v1/payments/webhook", true);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!PROTECTED.containsKey(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        String key = request.getRequestURI() + "|" + request.getRemoteAddr();
        long now = System.currentTimeMillis();
        int newCount = hits.compute(key, (k, b) -> (b == null || now - b.windowStart() > WINDOW_MS)
                        ? new Bucket(now, 1)
                        : new Bucket(b.windowStart(), b.count() + 1))
                .count();
        if (newCount > LIMIT) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"success\":false,\"error\":{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests. Try again shortly.\"}}");
            return;
        }
        chain.doFilter(request, response);
    }
}
