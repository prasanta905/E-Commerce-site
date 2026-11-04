package com.shopsphere.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Helper for extracting the current AuthUser from Spring Security context.
 */
@Component
public class CurrentUser {

    public AuthUser require() {
        Authentication authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser authUser) {
            return authUser;
        }
        throw new com.shopsphere.common.exception.BusinessException(401, "AUTH_REQUIRED", "Authentication required");
    }

    public Long requireUserId() {
        return require().id();
    }
}
