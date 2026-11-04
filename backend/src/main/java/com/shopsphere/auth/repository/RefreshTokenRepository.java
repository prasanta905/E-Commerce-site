package com.shopsphere.auth.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopsphere.auth.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    /** Load the active (non-revoked, non-expired) token for the presented jti. */
    Optional<RefreshToken> findByJti(String jti);

    List<RefreshToken> findByUserId(Long userId);

    /** Revoke an entire token family (reuse ⇒ assume theft). */
    List<RefreshToken> findByFamilyId(String familyId);
}
