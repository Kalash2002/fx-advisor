package com.fxadvisor.auth.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity for the `refresh_tokens` table.
 *
 * REFRESH TOKEN ROTATION PROTOCOL:
 * 1. On login: create RefreshToken{token=UUID, revoked=false, rotatedTo=null}
 * 2. On /auth/refresh:
 *    a. Load token from DB
 *    b. If revoked=true OR rotatedTo != null → REPLAY ATTACK → revoke ALL user tokens
 *    c. If valid: create new RefreshToken, set old.rotatedTo = new.token, old.revoked = true
 *    d. Return new access token + new refresh token
 * 3. On /auth/logout: set revoked=true on all user's tokens
 *
 * deviceInfo stores the User-Agent header for audit purposes.
 * rotatedTo chains the token lineage — useful for audit trail of who used what token.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String token;  // UUID string

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "rotated_to", length = 36)
    private String rotatedTo;  // UUID of the successor token (set on rotation)

    @Column(name = "device_info", length = 500)
    private String deviceInfo;  // User-Agent header for audit

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected RefreshToken() {}

    public RefreshToken(String token, Long userId, LocalDateTime expiresAt, String deviceInfo) {
        this.token = token;
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.deviceInfo = deviceInfo;
    }

    public Long getId() { return id; }
    public String getToken() { return token; }
    public Long getUserId() { return userId; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public boolean isRevoked() { return revoked; }
    public String getRotatedTo() { return rotatedTo; }
    public String getDeviceInfo() { return deviceInfo; }

    public void revoke() { this.revoked = true; }
    public void setRotatedTo(String newTokenUuid) { this.rotatedTo = newTokenUuid; }

    /**
     * A token is valid only if it is not revoked, not yet rotated, and not expired.
     * All three conditions must hold.
     */
    public boolean isValid() {
        return !revoked && rotatedTo == null && LocalDateTime.now().isBefore(expiresAt);
    }
}