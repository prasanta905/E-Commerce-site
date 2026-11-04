package com.shopsphere.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.auth.entity.EmailVerificationToken;
import com.shopsphere.auth.entity.PasswordResetToken;
import com.shopsphere.auth.entity.RefreshToken;
import com.shopsphere.auth.repository.EmailVerificationTokenRepository;
import com.shopsphere.auth.repository.PasswordResetTokenRepository;
import com.shopsphere.auth.repository.RefreshTokenRepository;
import com.shopsphere.common.exception.BusinessException;
import com.shopsphere.common.exception.ResourceNotFound;
import com.shopsphere.notification.MailService;
import com.shopsphere.user.entity.Role;
import com.shopsphere.user.entity.User;
import com.shopsphere.user.repository.RoleRepository;
import com.shopsphere.user.repository.UserRepository;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Authentication flows. Token handling rules:
 *  - access token: stateless JWT, 15 min
 *  - refresh token: random jti persisted server-side, rotated every use,
 *    reuse of a rotated jti ⇒ entire token family is revoked (theft signal).
 */
@Service
public class AuthService {

    private static final long REFRESH_TTL_MS = 7L * 24 * 60 * 60 * 1000;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final MailService mailService;
    private final String cookieSecure;
    private final String cookieSameSite;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       EmailVerificationTokenRepository emailVerificationTokenRepository,
                       MailService mailService,
                       @Value("${shopsphere.app.security.cookie.secure:false}") String cookieSecure,
                       @Value("${shopsphere.app.security.cookie.same-site:Strict}") String cookieSameSite) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.mailService = mailService;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
    }

    // ------------------------------------------------------------------ register
    @Transactional
    public AuthResponse register(RegisterRequest request, HttpServletResponse response) {
        if (userRepository.existsByEmailIgnoreCaseAndIsDeletedFalse(request.email())) {
            throw BusinessException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }

        Role userRole = roleRepository.findByName(Role.ROLE_USER)
                .orElseThrow(() -> new IllegalStateException("ROLE_USER missing — run migrations"));

        User user = User.builder()
                .email(request.email().trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .phone(request.phone())
                .isActive(true)
                .isEmailVerified(false)   // real verification via emailed token (non-blocking login)
                .isDeleted(false)
                .build();
        user.getRoles().add(userRole);
        userRepository.save(user);

        String rawToken = createEmailVerificationToken(user);
        mailService.send(user.getEmail(),
                "Verify your ShopSphere email",
                "Welcome to ShopSphere!\n\nUse this token to verify your email:\n" + rawToken
                        + "\n\nThe token is valid for 24 hours.",
                MailService.Intent.VERIFY_EMAIL);

        return issueSession(user, response);
    }

    // ------------------------------------------------------------------- login
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletResponse response) {
        User user = userRepository.findByEmailIgnoreCaseAndIsDeletedFalse(request.email())
                .orElseThrow(() -> new BusinessException(401, "AUTH_INVALID_CREDENTIALS",
                        "Invalid email or password"));

        if (!user.getIsActive()) {
            throw new BusinessException(403, "ACCOUNT_DISABLED",
                    "This account is disabled. Contact support for help.");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(401, "AUTH_INVALID_CREDENTIALS", "Invalid email or password");
        }

        return issueSession(user, response);
    }

    // ------------------------------------------------------------------ refresh
    @Transactional
    public AuthResponse refresh(String presentedJti, HttpServletResponse response) {
        if (presentedJti == null || presentedJti.isBlank()) {
            throw new BusinessException(401, "AUTH_REQUIRED", "Refresh cookie missing");
        }

        RefreshToken stored = refreshTokenRepository.findByJti(presentedJti)
                .orElseThrow(() -> new BusinessException(401, "REFRESH_INVALID",
                        "Refresh token not recognised"));

        if (stored.getRevokedAt() != null) {
            // Token reuse! Revoke the full family (assumed token theft).
            refreshFamilyRevoked(stored.getFamilyId());
            throw new BusinessException(401, "REFRESH_REUSE_DETECTED",
                    "Session cannot be renewed, please login again");
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(401, "REFRESH_EXPIRED", "Session expired, please login again");
        }
        User user = stored.getUser();
        if (user == null || !user.getIsActive() || user.getIsDeleted()) {
            throw new BusinessException(403, "ACCOUNT_DISABLED", "Account no longer active");
        }

        // rotate
        String newJti = UUID.randomUUID().toString();
        RefreshToken replacement = RefreshToken.builder()
                .user(user)
                .jti(newJti)
                .familyId(stored.getFamilyId())
                .expiresAt(Instant.now().plusMillis(REFRESH_TTL_MS))
                .build();
        refreshTokenRepository.save(replacement);

        stored.setRevokedAt(Instant.now());
        stored.setReplacedBy(newJti);

        String accessToken = jwtService.issueAccessToken(user.getId(), user.getEmail(), roleNames(user));
        setRefreshCookie(response, newJti);
        return AuthResponse.of(accessToken, user.getIsEmailVerified(), user.getEmail(),
                user.getFullName(), roleNames(user));
    }

    private void refreshFamilyRevoked(String familyId) {
        List<RefreshToken> family = refreshTokenRepository.findByFamilyId(familyId);
        family.forEach(t -> {
            if (t.getRevokedAt() == null) {
                t.setRevokedAt(Instant.now());
            }
        });
    }

    // ------------------------------------------------------------------- logout
    @Transactional
    public void logout(String presentedJti, HttpServletResponse response) {
        if (presentedJti != null && !presentedJti.isBlank()) {
            refreshTokenRepository.findByJti(presentedJti).ifPresent(t -> {
                refreshFamilyRevoked(t.getFamilyId());
            });
        }
        if (response != null) clearRefreshCookie(response);
    }

    // ------------------------------------------------------- forgot / reset
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        // Anti-enumeration: same log/behavior whether or not the email exists.
        userRepository.findByEmailIgnoreCaseAndIsDeletedFalse(request.email()).ifPresent(user -> {
            String raw = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
            PasswordResetToken token = PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(sha256(raw))
                    .expiresAt(Instant.now().plusSeconds(30 * 60))   // 30 min
                    .build();
            passwordResetTokenRepository.save(token);
            mailService.send(user.getEmail(), "ShopSphere password reset",
                    "Use this one-time token to reset your ShopSphere password:\n" + raw
                            + "\nValid for 30 minutes.",
                    MailService.Intent.RESET_PASSWORD);
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken stored = passwordResetTokenRepository.findByTokenHash(sha256(request.token()))
                .orElseThrow(() -> BusinessException.badRequest("RESET_TOKEN_INVALID",
                        "Reset link is not valid"));
        if (stored.getUsedAt() != null || stored.getExpiresAt().isBefore(Instant.now())) {
            throw BusinessException.badRequest("RESET_TOKEN_INVALID", "Reset link is no longer valid");
        }
        User user = stored.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        stored.setUsedAt(Instant.now());

        // Force re-authentication everywhere after a reset
        for (RefreshToken t : refreshTokenRepository.findByUserId(user.getId())) {
            if (t.getRevokedAt() == null) t.setRevokedAt(Instant.now());
        }
    }

    // -------------------------------------------------------- verify email
    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        EmailVerificationToken stored = emailVerificationTokenRepository.findByTokenHash(sha256(request.token()))
                .orElseThrow(() -> BusinessException.badRequest("VERIFY_TOKEN_INVALID", "Verification token is not valid"));
        if (stored.getVerifiedAt() != null) {
            throw BusinessException.badRequest("VERIFY_TOKEN_INVALID", "Token already used");
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw BusinessException.badRequest("VERIFY_TOKEN_INVALID", "Token expired — request a fresh one");
        }
        stored.setVerifiedAt(Instant.now());
        stored.getUser().setIsEmailVerified(true);
    }

    // ------------------------------------------------------------ internals
    private List<String> roleNames(User user) {
        return user.roles().stream().map(Role::getName).toList();
    }

    private AuthResponse issueSession(User user, HttpServletResponse response) {
        String newJti = UUID.randomUUID().toString();
        RefreshToken refresh = RefreshToken.builder()
                .user(user)
                .jti(newJti)
                .familyId(UUID.randomUUID().toString())
                .expiresAt(Instant.now().plusMillis(REFRESH_TTL_MS))
                .build();
        refreshTokenRepository.save(refresh);

        String access = jwtService.issueAccessToken(user.getId(), user.getEmail(), roleNames(user));
        if (response != null) setRefreshCookie(response, newJti);
        return AuthResponse.of(access, user.getIsEmailVerified(), user.getEmail(),
                user.getFullName(), roleNames(user));
    }

    private void setRefreshCookie(HttpServletResponse response, String jti) {
        String cookie = String.format("SS_REFRESH=%s; Path=/api/v1/auth; HttpOnly; SameSite=%s; Max-Age=%d%s",
                jti, cookieSameSite, REFRESH_TTL_MS / 1000, "true".equalsIgnoreCase(cookieSecure) ? "; Secure" : "");
        response.addHeader("Set-Cookie", cookie);
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie", "SS_REFRESH=; Path=/api/v1/auth; HttpOnly; SameSite=Strict; Max-Age=0");
    }

    @Transactional
    String createEmailVerificationToken(User user) {
        String raw = UUID.randomUUID().toString().replace("-", "");
        EmailVerificationToken token = EmailVerificationToken.builder()
                .user(user)
                .tokenHash(sha256(raw))
                .expiresAt(Instant.now().plusSeconds(24 * 60 * 60))
                .build();
        emailVerificationTokenRepository.save(token);
        return raw;
    }

    static String sha256(String raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
