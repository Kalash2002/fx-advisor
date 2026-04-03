package com.fxadvisor.auth.dto;

import java.util.List;

/**
 * Returned by /register and /login.
 * permissions[] lets the frontend know what the user can do without a separate
 * profile call. The frontend stores these to conditionally show admin UI.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        List<String> permissions
) {}