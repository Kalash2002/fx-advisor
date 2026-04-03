package com.fxadvisor.auth;

import com.fxadvisor.auth.service.JwtService;
import com.fxadvisor.core.exception.AuthException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    // Secret must be >= 32 chars for HS256
    private static final String TEST_SECRET = "test-secret-key-minimum-32-characters-long-abc";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, 900_000L); // 15 min
    }

    @Test
    void shouldGenerateValidAccessToken() {
        String token = jwtService.generateAccessToken(
                42L, List.of("PERMISSION_ANALYSE", "PERMISSION_AUDIT_VIEW_OWN"));
        assertNotNull(token);
        assertFalse(token.isBlank());
        // JWT format: 3 parts separated by dots
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    void shouldEmbedUserIdInSubClaim() {
        String token = jwtService.generateAccessToken(42L, List.of());
        Claims claims = jwtService.validateAndExtract(token);
        assertEquals(42L, jwtService.extractUserId(claims));
    }

    @Test
    void shouldEmbedPermissionsInClaims() {
        List<String> perms = List.of("PERMISSION_ANALYSE", "PERMISSION_ADMIN");
        String token = jwtService.generateAccessToken(1L, perms);
        Claims claims = jwtService.validateAndExtract(token);
        List<String> extracted = jwtService.extractPermissions(claims);
        assertTrue(extracted.containsAll(perms));
    }

    @Test
    void shouldThrowOnExpiredToken() throws InterruptedException {
        // Use 1ms TTL to create expired token
        JwtService shortTtlService = new JwtService(TEST_SECRET, 1L);
        String token = shortTtlService.generateAccessToken(1L, List.of());
        Thread.sleep(10); // Ensure expiry

        AuthException ex = assertThrows(AuthException.class,
                () -> jwtService.validateAndExtract(token));
        assertEquals("AUTH_TOKEN_EXPIRED", ex.getErrorCode());
    }

    @Test
    void shouldThrowOnTamperedToken() {
        String token = jwtService.generateAccessToken(1L, List.of());
        // Tamper with the payload by changing one character
        String[] parts = token.split("\\.");
        String tamperedToken = parts[0] + "." + parts[1] + "X" + "." + parts[2];

        AuthException ex = assertThrows(AuthException.class,
                () -> jwtService.validateAndExtract(tamperedToken));
        // Either malformed or signature error depending on base64 validity
        assertNotNull(ex.getErrorCode());
    }

    @Test
    void shouldThrowOnCompletelyInvalidToken() {
        assertThrows(AuthException.class,
                () -> jwtService.validateAndExtract("not.a.jwt"));
    }

    @Test
    void generateRefreshTokenReturnsUuidFormat() {
        String token = jwtService.generateRefreshToken();
        assertNotNull(token);
        // UUID format: 8-4-4-4-12 hex chars
        assertTrue(token.matches(
                        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
                "Refresh token must be UUID format, got: " + token);
    }

    @Test
    void twoRefreshTokensAreUnique() {
        String t1 = jwtService.generateRefreshToken();
        String t2 = jwtService.generateRefreshToken();
        assertNotEquals(t1, t2);
    }

    @Test
    void shouldThrowOnSecretTooShort() {
        assertThrows(IllegalStateException.class,
                () -> new JwtService("tooshort", 900_000L));
    }
}