package com.shopsphere.auth.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Rotating refresh token family. On each refresh the old row is revoked
 * (revoked_at) and points to the replacement jti — enabling reuse detection
 * and family-wide revocation on suspected theft.
 */
@Entity
@Table(name = "refresh_tokens",
       uniqueConstraints = {},
       indexes = { @Index(name = "ix_refresh_user", columnList = "user_id") })
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class RefreshToken {

    public static final String UNIQUE_INDEX_JTI_HINT = "ux_refresh_jti"; // enforced by migration

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private com.shopsphere.user.entity.User user;

    @Column(name = "jti", nullable = false, unique = true, length = 64)
    private String jti;

    @Column(name = "family_id", nullable = false, length = 64)
    private String familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** jti of the token this rotation replaced. */
    @Column(name = "replaced_by", length = 64)
    private String replacedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void stamp() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
