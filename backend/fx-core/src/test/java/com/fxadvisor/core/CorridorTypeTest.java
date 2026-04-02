package com.fxadvisor.core;

import com.fxadvisor.core.enums.CorridorType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CorridorTypeTest {

    @Test
    void shouldHaveExactlyFourValues() {
        assertEquals(4, CorridorType.values().length,
                "CorridorType must have exactly 4 values: SWIFT, UPI, CRYPTO_USDT, WISE");
    }

    @Test
    void shouldContainExpectedCorridors() {
        assertDoesNotThrow(() -> CorridorType.valueOf("SWIFT"));
        assertDoesNotThrow(() -> CorridorType.valueOf("UPI"));
        assertDoesNotThrow(() -> CorridorType.valueOf("CRYPTO_USDT"));
        assertDoesNotThrow(() -> CorridorType.valueOf("WISE"));
    }

    @Test
    void shouldNotHaveFeeFieldsOnEnum() {
        // The enum should have NO methods returning fee-related data.
        // This test verifies no fee methods exist by checking declared methods.
        var methods = CorridorType.class.getDeclaredMethods();
        for (var method : methods) {
            String name = method.getName().toLowerCase();
            assertFalse(name.contains("fee"),
                    "CorridorType must not have fee methods. Found: " + method.getName());
            assertFalse(name.contains("spread"),
                    "CorridorType must not have spread methods. Found: " + method.getName());
            assertFalse(name.contains("hours"),
                    "CorridorType must not have hours methods. Found: " + method.getName());
        }
    }
}