package com.fxadvisor.core.domain;

import com.fxadvisor.core.enums.Urgency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Immutable value object representing a transfer analysis request.
 *
 * Created by AdvisorController from the incoming HTTP request body.
 * Passed to CorridorOrchestratorAgent, then to RateAgent for tool calling.
 *
 * DESIGN: The compact constructor is the single validation point.
 * If ANY constraint fails, an IllegalArgumentException is thrown immediately
 * before the object is constructed. This means a TransferRequest in memory
 * is ALWAYS valid — no need for null checks downstream.
 *
 * WHY BigDecimal for amount?
 * double/float use binary floating point. 1.1 + 2.2 in double = 3.3000000000000003.
 * Financial calculations require exact decimal arithmetic.
 * BigDecimal with HALF_UP rounding mode gives correct monetary results.
 *
 * @param sessionId       Redis key prefix for conversation history (UUID format)
 * @param amount          Transfer amount in sourceCurrency (must be positive)
 * @param sourceCurrency  ISO 4217 currency code (e.g., "USD")
 * @param targetCurrency  ISO 4217 currency code (e.g., "INR")
 * @param urgency         User's delivery speed preference
 * @param userId          Authenticated user ID from JWT sub claim
 */
public record TransferRequest(
        @NotBlank String sessionId,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String sourceCurrency,
        @NotBlank String targetCurrency,
        @NotNull Urgency urgency,
        @NotNull Long userId
) {
    /**
     * Compact constructor — runs BEFORE component fields are assigned.
     * Throws IllegalArgumentException on any violation, which prevents
     * construction of an invalid TransferRequest object.
     */
    public TransferRequest {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive, got: " + amount);
        }
        if (sourceCurrency == null || sourceCurrency.isBlank()) {
            throw new IllegalArgumentException("sourceCurrency must not be blank");
        }
        if (targetCurrency == null || targetCurrency.isBlank()) {
            throw new IllegalArgumentException("targetCurrency must not be blank");
        }
        if (sourceCurrency.equalsIgnoreCase(targetCurrency)) {
            throw new IllegalArgumentException(
                    "sourceCurrency and targetCurrency must differ, got: " + sourceCurrency);
        }
        if (urgency == null) {
            throw new IllegalArgumentException("urgency must not be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        // Normalize currency codes to uppercase for consistent tool calls
        sourceCurrency = sourceCurrency.toUpperCase();
        targetCurrency = targetCurrency.toUpperCase();
    }
}