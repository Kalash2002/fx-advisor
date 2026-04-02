package com.fxadvisor.core.exception;

/**
 * Thrown for authentication and authorization failures.
 *
 * Causes:
 * - JWT signature invalid (token tampered)
 * - JWT expired (past the 15-minute window)
 * - Refresh token not found in DB
 * - Refresh token already revoked (replay attack detected)
 * - Refresh token rotatedTo != null (already rotated — replay)
 *
 * SECURITY DESIGN: Login failure messages are intentionally vague.
 * AuthService.login() throws AuthException("Invalid credentials") for
 * BOTH wrong email and wrong password. This prevents user enumeration
 * (attacker cannot determine if an email is registered).
 *
 * Handling in GlobalExceptionHandler: maps to HTTP 401 UNAUTHORIZED.
 * JwtAuthFilter: on AuthException, writes 401 directly to response
 * (before Spring Security filter chain completes).
 */
public class AuthException extends FxAdvisorException {

    public AuthException(String message) {
        super("AUTH_ERROR", message);
    }

    public AuthException(String errorCode, String message) {
        super(errorCode, message);
    }

    public AuthException(String message, Throwable cause) {
        super("AUTH_ERROR", message, cause);
    }
}