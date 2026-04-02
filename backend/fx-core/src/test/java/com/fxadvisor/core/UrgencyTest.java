// backend/fx-core/src/test/java/com/fxadvisor/core/UrgencyTest.java
package com.fxadvisor.core;

import com.fxadvisor.core.enums.Urgency;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UrgencyTest {

    @Test
    void instantMaxHoursIsOne() {
        assertEquals(1, Urgency.INSTANT.getMaxHours());
    }

    @Test
    void sameDayMaxHoursIs24() {
        assertEquals(24, Urgency.SAME_DAY.getMaxHours());
    }

    @Test
    void standardMaxHoursIs72() {
        assertEquals(72, Urgency.STANDARD.getMaxHours());
    }

    @Test
    void urgencyValuesHaveCorrectOrder() {
        // INSTANT is most restrictive, STANDARD least restrictive
        assertTrue(Urgency.INSTANT.getMaxHours() < Urgency.SAME_DAY.getMaxHours());
        assertTrue(Urgency.SAME_DAY.getMaxHours() < Urgency.STANDARD.getMaxHours());
    }

    @Test
    void corridorEligibilityLogic() {
        // Simulate: UPI has typicalHours=1, SWIFT has typicalHours=72
        int upiHours = 1;
        int swiftHours = 72;

        // For INSTANT urgency (maxHours=1): UPI eligible, SWIFT not
        assertTrue(upiHours <= Urgency.INSTANT.getMaxHours());
        assertFalse(swiftHours <= Urgency.INSTANT.getMaxHours());

        // For STANDARD urgency (maxHours=72): both eligible
        assertTrue(upiHours <= Urgency.STANDARD.getMaxHours());
        assertTrue(swiftHours <= Urgency.STANDARD.getMaxHours());
    }
}