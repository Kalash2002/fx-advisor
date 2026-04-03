package com.fxadvisor.auth.service;

import com.fxadvisor.auth.dto.*;
import com.fxadvisor.auth.entity.*;
import com.fxadvisor.auth.repository.*;
import com.fxadvisor.core.exception.AuthException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Core authentication business logic.
 *
 * KEY SECURITY DECISIONS:
 *
 * 1. User enumeration prevention: login() throws the SAME message for wrong email
 *    and wrong password. An attacker cannot determine whether an email is registered.
 *
 * 2. Refresh token rotation + replay detection:
 *    - Each use of a refresh token issues a new pair and marks old one rotatedTo=newUUID
 *    - If a token is presented AFTER rotation (rotatedTo != null), it means either
 *      the client is buggy or an attacker is replaying a stolen token
 *    - Response: revoke ALL tokens for the user (nuclear option)
 *    - The legitimate user must log in again — a small UX cost for strong security
 *
 * 3. BCrypt: passwords are stored as BCrypt hashes (strength 10 configured in SecurityConfig).
 *    passwordEncoder.matches() is timing-safe — no timing oracle attack.
 */
@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.refresh-token-expiry-days:7}")
    private int refreshTokenExpiryDays;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registers a new user with ROLE_USER by default.
     * Throws AuthException with errorCode DUPLICATE_EMAIL if email is taken.
     */
    public AuthResponse register(RegisterRequest request, String deviceInfo) {
        if (userRepository.existsByEmail(request.email())) {
            throw new AuthException("DUPLICATE_EMAIL",
                    "An account with this email already exists");
        }

        User user = new User(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.fullName()
        );

        // Assign default ROLE_USER (must exist from V6 seed migration)
        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException(
                        "ROLE_USER not found in DB — check V6 Flyway migration"));
        user.addRole(userRole);

        User savedUser = userRepository.save(user);
        return issueTokenPair(savedUser, deviceInfo);
    }

    /**
     * Authenticates a user and issues a token pair.
     *
     * SECURITY: same error message for wrong email and wrong password
     * to prevent user enumeration attacks.
     */
    public AuthResponse login(LoginRequest request, String deviceInfo) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new AuthException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuthException("Invalid credentials");
        }

        if (!user.isEnabled()) {
            throw new AuthException("AUTH_ACCOUNT_DISABLED", "Account is disabled");
        }

        return issueTokenPair(user, deviceInfo);
    }

    /**
     * Rotates the refresh token.
     *
     * If the presented token has already been rotated (rotatedTo != null),
     * it means replay: revoke ALL tokens for the user and throw 401.
     */
    public TokenRefreshResponse refreshTokens(String oldRefreshToken, String deviceInfo) {
        RefreshToken stored = refreshTokenRepository.findByToken(oldRefreshToken)
                .orElseThrow(() -> new AuthException("AUTH_TOKEN_NOT_FOUND",
                        "Refresh token not found"));

        // Replay attack detection
        if (stored.getRotatedTo() != null || stored.isRevoked()) {
            // Nuclear revocation: invalidate ALL sessions for this user
            refreshTokenRepository.revokeAllByUserId(stored.getUserId());
            throw new AuthException("AUTH_TOKEN_REPLAYED",
                    "Refresh token already used. All sessions revoked. Please log in again.");
        }

        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AuthException("AUTH_TOKEN_EXPIRED", "Refresh token has expired");
        }

        // Issue new pair
        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new AuthException("User not found"));

        String newRefreshToken = jwtService.generateRefreshToken();
        String newAccessToken = jwtService.generateAccessToken(
                user.getId(), extractPermissionNames(user));

        // Rotate: mark old token as used, chain to new token
        stored.revoke();
        stored.setRotatedTo(newRefreshToken);

        // Create new refresh token record
        RefreshToken newToken = new RefreshToken(
                newRefreshToken,
                user.getId(),
                LocalDateTime.now().plusDays(refreshTokenExpiryDays),
                deviceInfo
        );
        refreshTokenRepository.save(newToken);

        return new TokenRefreshResponse(newAccessToken, newRefreshToken);
    }

    /**
     * Revokes all refresh tokens for the authenticated user.
     * The access token remains valid until it expires (max 15 min).
     * Frontend must discard stored tokens on logout.
     */
    public void logout(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    // ─── Private Helpers ─────────────────────────────────────────────────────

    private AuthResponse issueTokenPair(User user, String deviceInfo) {
        List<String> permissions = extractPermissionNames(user);
        String accessToken = jwtService.generateAccessToken(user.getId(), permissions);
        String refreshToken = jwtService.generateRefreshToken();

        RefreshToken tokenEntity = new RefreshToken(
                refreshToken,
                user.getId(),
                LocalDateTime.now().plusDays(refreshTokenExpiryDays),
                deviceInfo
        );
        refreshTokenRepository.save(tokenEntity);

        return new AuthResponse(accessToken, refreshToken, permissions);
    }

    private List<String> extractPermissionNames(User user) {
        return user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(com.fxadvisor.auth.entity.Permission::getName)
                .distinct()
                .toList();
    }
}