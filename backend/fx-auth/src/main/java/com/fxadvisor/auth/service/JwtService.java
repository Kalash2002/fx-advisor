package com.fxadvisor.auth.service;


import com.fxadvisor.core.exception.AuthException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * JWT lifecycle management.
 *
 * ACCESS TOKEN:
 * - Algorithm: HS256 (HMAC-SHA256) with the configured secret
 * - Claims: sub=userId, permissions=[...], iat, exp
 * - TTL: 15 minutes (900,000 ms)
 * - Never stored in DB — stateless
 *
 * REFRESH TOKEN:
 * - A UUID string (not a JWT)
 * - Stored in MySQL refresh_tokens table
 * - TTL: 7 days
 * - Revocable immediately by setting revoked=true
 *
 * WHY HS256? This is a single-server setup (modular monolith). HS256 (symmetric)
 * is simpler and faster than RS256 (asymmetric). RS256 would be needed if external
 * services needed to verify tokens without sharing the secret.
 *
 * SECRET KEY REQUIREMENT: Must be >= 256 bits (32 bytes) for HS256.
 * Generate with: openssl rand -base64 32
 * Store in environment variable JWT_SECRET, never in application.yml.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenExpiryMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiry-ms:900000}") long accessTokenExpiryMs) {

        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "JWT secret must be at least 32 characters. Set JWT_SECRET env variable.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiryMs = accessTokenExpiryMs;
    }

    /**
     * Generates a signed JWT access token.
     *
     * Permissions are embedded as a JSON array claim so JwtAuthFilter can
     * reconstruct GrantedAuthority list without a DB query.
     *
     * @param userId      The authenticated user's DB id (stored as 'sub' claim)
     * @param permissions List of permission name strings (e.g. ["PERMISSION_ANALYSE"])
     * @return Signed JWT string
     */
    public String generateAccessToken(Long userId, List<String> permissions) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("permissions", permissions)
                .issuedAt(new Date(now))
                .expiration(new Date(now + accessTokenExpiryMs))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Generates a refresh token (UUID string, NOT a JWT).
     * The UUID is stored in MySQL and sent to the client.
     * On /auth/refresh, the client presents this UUID; the DB record is validated.
     */
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * Validates the JWT and extracts all claims.
     *
     * Throws AuthException (subclass of RuntimeException) for:
     * - Expired token (ExpiredJwtException)
     * - Invalid signature (SignatureException)
     * - Malformed token (MalformedJwtException)
     * - Unsupported token (UnsupportedJwtException)
     *
     * The caller (JwtAuthFilter) catches AuthException and returns 401.
     *
     * @param token The JWT string (without "Bearer " prefix)
     * @return Parsed Claims object
     */
    public Claims validateAndExtract(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new AuthException("AUTH_TOKEN_EXPIRED", "Access token has expired");
        } catch (SignatureException e) {
            throw new AuthException("AUTH_TOKEN_INVALID", "JWT signature is invalid");
        } catch (MalformedJwtException e) {
            throw new AuthException("AUTH_TOKEN_MALFORMED", "JWT is malformed");
        } catch (UnsupportedJwtException e) {
            throw new AuthException("AUTH_TOKEN_UNSUPPORTED", "JWT algorithm not supported");
        } catch (JwtException e) {
            throw new AuthException("AUTH_TOKEN_ERROR", "JWT validation failed: " + e.getMessage());
        }
    }

    /**
     * Extracts the userId from a validated claims object.
     * The 'sub' claim is stored as a String — parse it back to Long.
     */
    public Long extractUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    /**
     * Extracts the permissions list from claims.
     * Returns empty list if claims are missing the permissions key (defensive).
     */
    @SuppressWarnings("unchecked")
    public List<String> extractPermissions(Claims claims) {
        Object perms = claims.get("permissions");
        if (perms instanceof List<?> list) {
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }
        return List.of();
    }
}