package com.fxadvisor.rate;

import com.fxadvisor.core.domain.CorridorQuote;
import com.fxadvisor.core.enums.CorridorType;
import com.fxadvisor.core.exception.RateFetchException;
import com.fxadvisor.rate.client.FrankfurterClient;
import com.fxadvisor.rate.tools.RateFetchTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateFetchToolsTest {

    @Mock
    private FrankfurterClient frankfurterClient;

    private RateFetchTools tools;

    @BeforeEach
    void setUp() {
        tools = new RateFetchTools(frankfurterClient);
    }

    // ─── fetchExchangeRate tests ────────────────────────────────────────

    @Test
    void fetchExchangeRateDelegatesToClient() {
        BigDecimal expectedRate = new BigDecimal("83.42");
        when(frankfurterClient.getRate("USD", "INR")).thenReturn(expectedRate);

        BigDecimal result = tools.fetchExchangeRate("USD", "INR");

        assertEquals(expectedRate, result);
        verify(frankfurterClient).getRate("USD", "INR");
    }

    @Test
    void fetchExchangeRatePropagatesRateFetchException() {
        when(frankfurterClient.getRate("USD", "INVALID"))
                .thenThrow(new RateFetchException("No rate found for USD/INVALID"));

        RateFetchException ex = assertThrows(RateFetchException.class,
                () -> tools.fetchExchangeRate("USD", "INVALID"));
        assertEquals("RATE_FETCH_FAILED", ex.getErrorCode());
    }

    // ─── calculateEffectiveRate — fee formula tests ─────────────────────

    @Test
    void upiCorridorHasZeroFeesAndZeroSpread() {
        BigDecimal midRate = new BigDecimal("83.42");

        CorridorQuote quote = tools.calculateEffectiveRate(
                midRate, "UPI", new BigDecimal("1000"), "STANDARD");

        assertEquals(CorridorType.UPI, quote.corridorType());
        // UPI has 0% spread — effective rate must equal mid-market rate
        assertEquals(0, quote.effectiveRate().compareTo(midRate),
                "UPI effective rate should equal mid-market rate (zero spread)");
        // UPI has zero fees
        assertEquals(0, quote.feeUsd().compareTo(BigDecimal.ZERO),
                "UPI should have zero fees");
        // UPI delivers the most (no spread, no fee deduction)
        assertTrue(quote.receivedAmount().compareTo(new BigDecimal("8000")) > 0,
                "UPI receivedAmount should be > 8000 INR for 1000 USD at ~83 rate");
        assertTrue(quote.isEligible(),
                "UPI (1h) should be eligible for STANDARD urgency (72h max)");
    }

    @Test
    void swiftCorridorAppliesSpreadAndFlatFee() {
        BigDecimal midRate = new BigDecimal("83.42");

        CorridorQuote quote = tools.calculateEffectiveRate(
                midRate, "SWIFT", new BigDecimal("1000"), "STANDARD");

        assertEquals(CorridorType.SWIFT, quote.corridorType());
        // SWIFT has 1.5% spread — effective rate must be strictly less than mid rate
        assertTrue(quote.effectiveRate().compareTo(midRate) < 0,
                "SWIFT effective rate should be below mid rate due to 1.5% spread");
        // SWIFT has $25 flat fee minimum
        assertTrue(quote.feeUsd().compareTo(new BigDecimal("25")) >= 0,
                "SWIFT fee should be at least $25 flat fee");
        // Received amount is positive
        assertTrue(quote.receivedAmount().compareTo(BigDecimal.ZERO) > 0);
        // SWIFT is 72h — eligible for STANDARD (72h max)
        assertTrue(quote.isEligible());
        assertEquals(72, quote.estimatedHours());
    }

    @Test
    void swiftIsIneligibleForInstantUrgency() {
        BigDecimal midRate = new BigDecimal("83.42");

        CorridorQuote quote = tools.calculateEffectiveRate(
                midRate, "SWIFT", new BigDecimal("1000"), "INSTANT");

        // SWIFT takes 72h but INSTANT requires <= 1h
        assertFalse(quote.isEligible(),
                "SWIFT (72h) should be ineligible for INSTANT urgency (1h max)");
    }

    @Test
    void upiIsEligibleForInstantUrgency() {
        BigDecimal midRate = new BigDecimal("83.42");

        CorridorQuote quote = tools.calculateEffectiveRate(
                midRate, "UPI", new BigDecimal("1000"), "INSTANT");

        // UPI takes 1h — exactly meets INSTANT requirement
        assertTrue(quote.isEligible(),
                "UPI (1h) should be eligible for INSTANT urgency (1h max)");
        assertEquals(1, quote.estimatedHours());
    }

    @Test
    void wiseIsIneligibleForInstantButEligibleForSameDay() {
        BigDecimal midRate = new BigDecimal("83.42");

        CorridorQuote instantQuote = tools.calculateEffectiveRate(
                midRate, "WISE", new BigDecimal("1000"), "INSTANT");
        assertFalse(instantQuote.isEligible(),
                "WISE (24h) should be ineligible for INSTANT urgency (1h max)");

        CorridorQuote sameDayQuote = tools.calculateEffectiveRate(
                midRate, "WISE", new BigDecimal("1000"), "SAME_DAY");
        assertTrue(sameDayQuote.isEligible(),
                "WISE (24h) should be eligible for SAME_DAY urgency (24h max)");
    }

    @Test
    void cryptoUsdtEligibleForInstant() {
        BigDecimal midRate = new BigDecimal("83.42");

        CorridorQuote quote = tools.calculateEffectiveRate(
                midRate, "CRYPTO_USDT", new BigDecimal("1000"), "INSTANT");

        assertTrue(quote.isEligible(), "CRYPTO_USDT (1h) should be eligible for INSTANT");
        assertEquals(CorridorType.CRYPTO_USDT, quote.corridorType());
    }

    @Test
    void receivedAmountNeverExceedsGrossAmount() {
        // Gross amount = amount * midMarketRate (before any fees or spread)
        BigDecimal midRate = new BigDecimal("83.42");
        BigDecimal amount = new BigDecimal("1000");
        BigDecimal grossAmount = amount.multiply(midRate);

        for (String corridor : new String[]{"SWIFT", "UPI", "CRYPTO_USDT", "WISE"}) {
            CorridorQuote quote = tools.calculateEffectiveRate(
                    midRate, corridor, amount, "STANDARD");
            assertTrue(quote.receivedAmount().compareTo(grossAmount) <= 0,
                    corridor + ": receivedAmount exceeds gross amount — fee calculation bug. "
                            + "receivedAmount=" + quote.receivedAmount()
                            + " grossAmount=" + grossAmount);
        }
    }

    @Test
    void upiDeliversMostAmongAllCorridors() {
        // UPI has zero spread and zero fees — should always deliver the most
        BigDecimal midRate = new BigDecimal("83.42");
        BigDecimal amount = new BigDecimal("1000");

        CorridorQuote upiQuote = tools.calculateEffectiveRate(
                midRate, "UPI", amount, "STANDARD");

        for (String corridor : new String[]{"SWIFT", "CRYPTO_USDT", "WISE"}) {
            CorridorQuote other = tools.calculateEffectiveRate(
                    midRate, corridor, amount, "STANDARD");
            assertTrue(upiQuote.receivedAmount().compareTo(other.receivedAmount()) >= 0,
                    "UPI should deliver >= " + corridor + " but got UPI="
                            + upiQuote.receivedAmount() + " vs " + corridor + "=" + other.receivedAmount());
        }
    }

    @Test
    void allCorridorTypesAreRecognised() {
        BigDecimal midRate = new BigDecimal("83.42");
        BigDecimal amount = new BigDecimal("500");

        // Should not throw IllegalArgumentException for any valid corridor name
        assertDoesNotThrow(() -> tools.calculateEffectiveRate(midRate, "SWIFT", amount, "STANDARD"));
        assertDoesNotThrow(() -> tools.calculateEffectiveRate(midRate, "UPI", amount, "STANDARD"));
        assertDoesNotThrow(() -> tools.calculateEffectiveRate(midRate, "CRYPTO_USDT", amount, "STANDARD"));
        assertDoesNotThrow(() -> tools.calculateEffectiveRate(midRate, "WISE", amount, "STANDARD"));
    }
}