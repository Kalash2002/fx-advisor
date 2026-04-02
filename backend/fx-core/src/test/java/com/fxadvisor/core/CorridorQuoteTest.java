package com.fxadvisor.core;

import com.fxadvisor.core.domain.CorridorQuote;
import com.fxadvisor.core.enums.CorridorType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CorridorQuoteTest {

    @Test
    void shouldCreateValidQuote() {
        CorridorQuote quote = new CorridorQuote(
                CorridorType.UPI,
                new BigDecimal("83.42"),
                new BigDecimal("83.42"),  // UPI has zero spread
                BigDecimal.ZERO,
                new BigDecimal("8342.00"),
                1,
                null,
                true
        );

        assertEquals(CorridorType.UPI, quote.corridorType());
        assertEquals(new BigDecimal("8342.00"), quote.receivedAmount());
        assertTrue(quote.isEligible());
        assertNull(quote.complianceNote());
    }

    @Test
    void ineligibleFactoryCreatesCorrectQuote() {
        CorridorQuote quote = CorridorQuote.ineligible(CorridorType.SWIFT, 72);
        assertEquals(CorridorType.SWIFT, quote.corridorType());
        assertFalse(quote.isEligible());
        assertEquals(72, quote.estimatedHours());
        assertNotNull(quote.complianceNote());
    }

    @Test
    void shouldThrowOnNullCorridorType() {
        assertThrows(IllegalArgumentException.class, () ->
                new CorridorQuote(null, BigDecimal.ONE, BigDecimal.ONE,
                        BigDecimal.ZERO, BigDecimal.ONE, 1, null, true));
    }

    @Test
    void shouldThrowOnZeroMidMarketRate() {
        assertThrows(IllegalArgumentException.class, () ->
                new CorridorQuote(CorridorType.UPI, BigDecimal.ZERO, BigDecimal.ONE,
                        BigDecimal.ZERO, BigDecimal.ONE, 1, null, true));
    }
}