package com.fxadvisor.auth.controller;

import com.fxadvisor.auth.dto.*;
import com.fxadvisor.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication endpoints.
 *
 * All endpoints under /api/v1/auth/** are marked permitAll() in SecurityConfig,
 * so JwtAuthFilter will NOT block unauthenticated access here.
 *
 * The User-Agent header is captured as deviceInfo for audit trail in refresh_tokens.
 * This helps admins identify which device issued which token during incident response.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/v1/auth/register
     * Registers a new user account with ROLE_USER.
     * Returns 409 CONFLICT if email is already registered.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        String deviceInfo = httpRequest.getHeader("User-Agent");
        AuthResponse response = authService.register(request, deviceInfo);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/v1/auth/login
     * Returns 200 with token pair, or 401 for any credential mismatch.
     * Error message is identical for wrong email and wrong password (no user enumeration).
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String deviceInfo = httpRequest.getHeader("User-Agent");
        AuthResponse response = authService.login(request, deviceInfo);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/auth/refresh
     * Rotates the refresh token. Client sends the current refresh token,
     * receives a new access token + new refresh token.
     * Presenting an already-used token triggers full session revocation.
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenRefreshResponse> refresh(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest httpRequest) {
        String deviceInfo = httpRequest.getHeader("User-Agent");
        TokenRefreshResponse response = authService.refreshTokens(
                request.refreshToken(), deviceInfo);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/auth/logout
     * Revokes all refresh tokens for the authenticated user.
     * The JWT filter populates SecurityContext, so @AuthenticationPrincipal
     * gives us the userId (set as principal in JwtAuthFilter).
     * Returns 204 NO CONTENT — no body.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Long userId) {
        authService.logout(userId);
        return ResponseEntity.noContent().build();
    }
}