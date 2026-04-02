package com.fxadvisor.core;

import com.fxadvisor.core.domain.TransferRequest;
import com.fxadvisor.core.enums.Urgency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class TransferRequestTest {

    private TransferRequest validRequest() {
        return new TransferRequest(
                "session-uuid-123",
                new BigDecimal("1000.00"),
                "USD",
                "INR",
                Urgency.STANDARD,
                42L
        );
    }

    @Test
    void shouldCreateValidRequest() {
        TransferRequest req = validRequest();
        assertEquals("session-uuid-123", req.sessionId());
        assertEquals(new BigDecimal("1000.00"), req.amount());
        assertEquals("USD", req.sourceCurrency());
        assertEquals("INR", req.targetCurrency());
        assertEquals(Urgency.STANDARD, req.urgency());
        assertEquals(42L, req.userId());
    }

    @Test
    void shouldNormalizeCurrencyToUppercase() {
        TransferRequest req = new TransferRequest(
                "session-1", new BigDecimal("100"), "usd", "inr", Urgency.INSTANT, 1L);
        assertEquals("USD", req.sourceCurrency());
        assertEquals("INR", req.targetCurrency());
    }

    @Test
    void shouldThrowOnNullAmount() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new TransferRequest("session-1", null, "USD", "INR", Urgency.STANDARD, 1L));
        assertTrue(ex.getMessage().contains("amount"));
    }

    @Test
    void shouldThrowOnZeroAmount() {
        assertThrows(IllegalArgumentException.class, () ->
                new TransferRequest("session-1", BigDecimal.ZERO, "USD", "INR", Urgency.STANDARD, 1L));
    }

    @Test
    void shouldThrowOnNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () ->
                new TransferRequest("session-1", new BigDecimal("-50"), "USD", "INR", Urgency.STANDARD, 1L));
    }

    @Test
    void shouldThrowOnBlankSessionId() {
        assertThrows(IllegalArgumentException.class, () ->
                new TransferRequest("  ", new BigDecimal("100"), "USD", "INR", Urgency.STANDARD, 1L));
    }

    @Test
    void shouldThrowOnSameCurrency() {
        assertThrows(IllegalArgumentException.class, () ->
                new TransferRequest("session-1", new BigDecimal("100"), "USD", "USD", Urgency.STANDARD, 1L));
    }

    @Test
    void shouldThrowOnNullUrgency() {
        assertThrows(IllegalArgumentException.class, () ->
                new TransferRequest("session-1", new BigDecimal("100"), "USD", "INR", null, 1L));
    }

    @Test
    void shouldThrowOnNullUserId() {
        assertThrows(IllegalArgumentException.class, () ->
                new TransferRequest("session-1", new BigDecimal("100"), "USD", "INR", Urgency.STANDARD, null));
    }
}