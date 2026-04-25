package com.fxadvisor.rate.tools;

import com.fxadvisor.core.domain.CorridorQuote;
import com.fxadvisor.core.enums.CorridorType;
import com.fxadvisor.rate.client.FrankfurterClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Spring AI @Tool methods for FX rate fetching and fee computation.
 *
 * These methods serve dual purpose:
 *
 * 1. NATIVE TOOL CALLING (OpenAI/Anthropic):
 *    Spring AI reads the @Tool annotations, generates JSON schema for each method,
 *    includes them in the tools[] array of the API request, and auto-invokes
 *    the methods when the LLM returns tool_calls in its response.
 *
 * 2. SIMULATED REACT LOOP (Ollama):
 *    RateAgent.invokeToolByName() calls these methods directly after parsing
 *    the XML tool-call tags from the model's text response. Same Java code,
 *    different dispatch mechanism.
 *
 * SPRINT 5 NOTE on fee values:
 * calculateEffectiveRate uses hardcoded placeholder fee config for each corridor.
 * In Sprint 7, this method will be updated to load CorridorConfig from MySQL
 * via CorridorConfigService (with Redis cache). The @Tool signature and return
 * type (CorridorQuote) remain identical — only the internal source of fee data
 * changes. No changes needed in RateAgent or any callers.
 *
 * HOW @Tool WORKS — what the LLM sees:
 * Spring AI converts each annotated method into this JSON schema structure:
 * {
 *   "name": "fetchExchangeRate",
 *   "description": "Fetches the current mid-market exchange rate...",
 *   "parameters": {
 *     "type": "object",
 *     "properties": {
 *       "sourceCurrency": { "type": "string", "description": "ISO 4217..." },
 *       "targetCurrency": { "type": "string", "description": "ISO 4217..." }
 *     }
 *   }
 * }
 * The LLM reads this schema and generates tool_call JSON with matching field names.
 */
@Component
public class RateFetchTools {

    private final FrankfurterClient frankfurterClient;

    public RateFetchTools(FrankfurterClient frankfurterClient) {
        this.frankfurterClient = frankfurterClient;
    }

    /**
     * Tool 1 — Fetch the current mid-market exchange rate.
     *
     * The LLM calls this FIRST. All corridor fee calculations depend on
     * this rate. By making it a tool call (not a pre-computed value), we
     * guarantee the analysis is based on today's live rate — never a
     * stale value from the LLM's training data.
     *
     * INTERVIEW: Why must the LLM call this instead of pre-computing it?
     * LLMs have a training cutoff. If we don't provide this tool, the LLM
     * guesses a rate from training data — potentially months or years stale.
     * Forcing a tool call guarantees real-time accuracy.
     */
    @Tool(description = """
            Fetches the current mid-market exchange rate between two currencies
            from the Frankfurter API. Returns the rate as a decimal number.
            Always call this tool FIRST before computing any corridor fees.
            Never guess or use historical rates — always call this tool.
            Example: fetching USD to INR returns approximately 83.42.
            """)
    public BigDecimal fetchExchangeRate(
            @ToolParam(description = "ISO 4217 source currency code, e.g. USD, EUR, GBP")
            String sourceCurrency,
            @ToolParam(description = "ISO 4217 target currency code, e.g. INR, PHP, MXN")
            String targetCurrency) {

        return frankfurterClient.getRate(sourceCurrency, targetCurrency);
    }

    /**
     * Tool 2 — Calculate the effective rate and received amount for one corridor.
     *
     * Must be called once per corridor after fetchExchangeRate.
     * Valid corridorType values: SWIFT, UPI, CRYPTO_USDT, WISE.
     *
     * INTERVIEW: Why return a rich CorridorQuote instead of just the received amount?
     * The LLM needs the full breakdown to write a useful recommendation:
     * effective rate (after spread), fee amount, received amount, delivery hours,
     * and eligibility against the user's urgency requirement. A single number
     * would force the LLM to guess the breakdown — incorrect for a financial app.
     *
     * SPRINT 5: hardcoded fees. Sprint 7: DB-driven via CorridorConfigService.
     */
    @Tool(description = """
            Calculates the effective exchange rate and received amount for a specific
            transfer corridor after applying its spread and fees.
            Call this for EACH of the four corridors: SWIFT, UPI, CRYPTO_USDT, WISE.
            Call after fetchExchangeRate — pass the rate returned by that tool.
            Returns a complete quote: effective rate, fees in USD, received amount
            in target currency, estimated delivery hours, and urgency eligibility.
            """)
    public CorridorQuote calculateEffectiveRate(
            @ToolParam(description = "Mid-market rate from fetchExchangeRate, e.g. 83.42")
            BigDecimal midMarketRate,
            @ToolParam(description = "Corridor: SWIFT, UPI, CRYPTO_USDT, or WISE")
            String corridorType,
            @ToolParam(description = "Transfer amount in source currency, e.g. 1000.00")
            BigDecimal amount,
            @ToolParam(description = "Delivery urgency: INSTANT, SAME_DAY, or STANDARD")
            String urgency) {

        // Sprint 5: placeholder fee config — Sprint 7 replaces with DB-driven FeeEngine
        return switch (CorridorType.valueOf(corridorType.toUpperCase())) {
            case SWIFT -> computeQuote(
                    CorridorType.SWIFT, midMarketRate, amount,
                    new BigDecimal("1.5"),    // 1.5% spread
                    new BigDecimal("25.00"),  // $25 flat fee
                    BigDecimal.ZERO,          // 0% pct fee
                    72,                       // 72 hours delivery
                    urgency);

            case UPI -> computeQuote(
                    CorridorType.UPI, midMarketRate, amount,
                    BigDecimal.ZERO,          // 0% spread
                    BigDecimal.ZERO,          // $0 flat fee
                    BigDecimal.ZERO,          // 0% pct fee
                    1,                        // 1 hour delivery
                    urgency);

            case CRYPTO_USDT -> computeQuote(
                    CorridorType.CRYPTO_USDT, midMarketRate, amount,
                    new BigDecimal("0.2"),    // 0.2% spread
                    new BigDecimal("2.00"),   // $2 flat fee
                    new BigDecimal("0.1"),    // 0.1% pct fee
                    1,                        // 1 hour delivery
                    urgency);

            case WISE -> computeQuote(
                    CorridorType.WISE, midMarketRate, amount,
                    new BigDecimal("0.5"),    // 0.5% spread
                    new BigDecimal("5.00"),   // $5 flat fee
                    new BigDecimal("0.45"),   // 0.45% pct fee
                    24,                       // 24 hours delivery
                    urgency);
        };
    }

    // ─── Private Helpers ──────────────────────────────────────────────────────

    /**
     * Core fee calculation formula. All math uses BigDecimal with HALF_UP rounding.
     * This is the same formula FeeEngine will use in Sprint 7 — the only difference
     * is where the input values (spread, fees) come from (hardcoded vs DB).
     *
     *   effectiveRate  = midMarketRate × (1 − spreadPct / 100)
     *   totalFeeUsd    = flatFeeUsd + (amount × pctFee / 100)
     *   receivedAmount = (amount − totalFeeUsd) × effectiveRate
     *
     * INTERVIEW: Why BigDecimal and not double for these calculations?
     * double uses IEEE 754 binary floating-point. 0.1 + 0.2 in double = 0.30000000000000004.
     * For financial calculations this is unacceptable — rounding errors accumulate.
     * BigDecimal uses base-10 arithmetic with explicit scale and rounding mode.
     * HALF_UP matches standard banking rounding conventions (round half away from zero).
     */
    private CorridorQuote computeQuote(
            CorridorType corridorType,
            BigDecimal midMarketRate,
            BigDecimal amount,
            BigDecimal spreadPct,
            BigDecimal flatFeeUsd,
            BigDecimal pctFee,
            int typicalHours,
            String urgency) {

        final BigDecimal HUNDRED = new BigDecimal("100");

        // effectiveRate = midMarketRate * (1 - spreadPct/100)
        BigDecimal effectiveRate = midMarketRate.multiply(
                BigDecimal.ONE.subtract(
                        spreadPct.divide(HUNDRED, 8, RoundingMode.HALF_UP)));

        // totalFeeUsd = flatFeeUsd + (amount * pctFee/100)
        BigDecimal totalFeeUsd = flatFeeUsd.add(
                amount.multiply(pctFee.divide(HUNDRED, 8, RoundingMode.HALF_UP)));

        // receivedAmount = (amount - totalFeeUsd) * effectiveRate
        BigDecimal receivedAmount = amount.subtract(totalFeeUsd)
                .multiply(effectiveRate)
                .setScale(2, RoundingMode.HALF_UP);

        // Eligibility check: does corridor delivery time fit urgency?
        int maxHours = switch (urgency.toUpperCase()) {
            case "INSTANT"  -> 1;
            case "SAME_DAY" -> 24;
            default         -> 72; // STANDARD
        };
        boolean isEligible = typicalHours <= maxHours;

        return new CorridorQuote(
                corridorType,
                midMarketRate.setScale(4, RoundingMode.HALF_UP),
                effectiveRate.setScale(4, RoundingMode.HALF_UP),
                totalFeeUsd.setScale(2, RoundingMode.HALF_UP),
                receivedAmount,
                typicalHours,
                null,      // complianceNote — populated in Sprint 6
                isEligible
        );
    }
}
