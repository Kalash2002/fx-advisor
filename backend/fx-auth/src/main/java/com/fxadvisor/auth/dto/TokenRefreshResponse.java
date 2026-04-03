package com.fxadvisor.auth.dto;

/** Returned by /refresh — no permissions needed, same user, same role. */
public record TokenRefreshResponse(String accessToken, String refreshToken) {}