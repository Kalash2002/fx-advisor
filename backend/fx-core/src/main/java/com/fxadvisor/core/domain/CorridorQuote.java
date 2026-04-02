package com.fxadvisor.core.domain;

import com.fxadvisor.core.enums.CorridorType;

import java.math.BigDecimal;

/**
 * Immutable result object for one corridor's computed transfer quote.
 *
 * Created by FeeEngine after calculating all fees and rates for a corridor.
 * Passed back to RateAgent as the @Tool return value, which the LLM then
 * reasons over to produce the final ranked recommendation.
 *
 * The LLM sees all fields as part of the tool result JSON. It uses:
 * - receivedAmount to rank corridors by value delivered
 * - estimatedHours to check urgency eligibility
 * - complianceNote to surface regulatory warnings
 * - isEligible to exclude corridors that don't meet urgency requirements
 *
 * @param corridorType    Which corridor this quote is for
 * @param midMarketRate   Raw exchange rate from Frankfurter (no spread applied)
 * @param effectiveRate   Rate after FX spread deduction (what the user actually gets)
 * @param feeUsd          Total fee in USD equivalent
 * @param receivedAmount  Final amount received by beneficiary in targetCurrency
 * @param estimatedHours  Expected delivery time in hours
 * @param complianceNote  RAG-sourced compliance text, or null if no compliance check needed
 * @param isEligible      False if corridor's typicalHours exceeds user's urgency maxHours
 */
public record CorridorQuote(
        CorridorType corridorType,
        BigDecimal midMarketRate,
        BigDecimal effectiveRate,
        BigDecimal feeUsd,
        BigDecimal receivedAmount,
        int estimatedHours,
        String complianceNote,
        boolean isEligible
) {
    public CorridorQuote {
        if (corridorType == null) {
            throw new IllegalArgumentException("corridorType must not be null");
        }
        if (midMarketRate == null || midMarketRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("midMarketRate must be positive");
        }
        if (effectiveRate == null || effectiveRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("effectiveRate must be positive");
        }
        if (feeUsd == null || feeUsd.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("feeUsd must be >= 0");
        }
        if (receivedAmount == null || receivedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("receivedAmount must be >= 0");
        }
        if (estimatedHours <= 0) {
            throw new IllegalArgumentException("estimatedHours must be positive");
        }
    }

    /**
     * Convenience factory for an ineligible quote (urgency not met).
     * Used by CorridorComparator when a corridor's typicalHours exceeds maxHours.
     */
    public static CorridorQuote ineligible(CorridorType corridorType, int typicalHours) {
        return new CorridorQuote(
                corridorType,
                BigDecimal.ONE,   // placeholder — not used by LLM for ineligible
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                typicalHours,
                "Corridor does not meet urgency requirement",
                false
        );
    }
}