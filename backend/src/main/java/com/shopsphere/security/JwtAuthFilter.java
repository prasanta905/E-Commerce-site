package com.shopsphere.security;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.shopsphere.auth.JwtService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Stateless bearer-token filter. Access tokens live in the Authorization
 * header only; refresh tokens are cookie-scoped and handled by /auth/refresh.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        String token = header.substring(7);

        try {
            Claims claims = jwtService.parse(token);
            Optional<Long> userIdOpt = jwtService.extractUserId(claims);
            if (userIdOpt.isEmpty()) {
                chain.doFilter(request, response);
                return;
            }
            Long userId = userIdOpt.get();
            List<SimpleGrantedAuthority> authorities = jwtService.extractRoles(claims).stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();

            AuthUser principal = new AuthUser(userId, claims.getSubject(), authorities);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException e) {
            // Invalid/expired token → anonymous request. Protected routes will 401.
            SecurityContextHolder.clearContext();
        }

        chain.doFilter(request, response);
    }
}
