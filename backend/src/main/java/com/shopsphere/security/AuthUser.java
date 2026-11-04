package com.shopsphere.security;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security principal — carries the DB user id and the role list.
 */
public record AuthUser(Long id, String email, List<SimpleGrantedAuthority> authorities)
        implements UserDetails {

    @Override
    public java.util.Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null; // stateless
    }

    @Override
    public String getUsername() {
        return email;
    }

    public boolean isAdmin() {
        return authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
