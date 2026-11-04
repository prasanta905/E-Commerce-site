package com.shopsphere.user;

/** Safe out-of-band shape (no password hash, no flags). */
public record ProfileResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        boolean emailVerified
) {}
