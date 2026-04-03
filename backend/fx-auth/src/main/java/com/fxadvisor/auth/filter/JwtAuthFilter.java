package com.fxadvisor.auth.filter;

import com.fxadvisor.auth.service.JwtService;
import com.fxadvisor.core.exception.AuthException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT authentication filter — runs once per request, before all controllers.
 *
 * FILTER FLOW:
 * 1. Extract "Authorization: Bearer <token>" header
 * 2. If header missing or malformed → skip filter (let Spring Security handle as anonymous)
 * 3. Validate JWT via JwtService.validateAndExtract()
 * 4. If valid: extract userId + permissions → set SecurityContext
 * 5. If invalid: write 401 directly to response (do NOT continue filter chain)
 *
 * WHY read permissions from JWT claims (not DB)?
 * Loading permissions from MySQL on every request adds 5–20ms at P99.
 * With permissions embedded in the signed JWT, this filter completes in < 1ms.
 * The tradeoff: permission changes propagate after the 15-min access token TTL.
 *
 * WHY OncePerRequestFilter?
 * Guarantees exactly one execution per request, even if the filter is invoked
 * multiple times by async dispatch chains.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        // No bearer token — pass through as unauthenticated (Spring Security handles 401/403)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7); // Strip "Bearer "

        try {
            Claims claims = jwtService.validateAndExtract(token);
            Long userId = jwtService.extractUserId(claims);
            List<String> permissions = jwtService.extractPermissions(claims);

            // Build authorities from embedded permissions
            List<SimpleGrantedAuthority> authorities = permissions.stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();

            // Set SecurityContext — userId as principal, permissions as authorities
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId,          // principal — retrieved via SecurityContextHolder in controllers
                            null,            // credentials — not needed post-authentication
                            authorities      // GrantedAuthority list for @PreAuthorize checks
                    );

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (AuthException e) {
            // Token is invalid — clear context and return 401
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                    """
                    {"error":"%s","message":"%s"}
                    """.formatted(e.getErrorCode(), e.getMessage()));
            return; // Do NOT continue filter chain
        }

        filterChain.doFilter(request, response);
    }
}