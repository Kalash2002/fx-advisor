package com.fxadvisor.core.enums;

/**
 * Transfer urgency levels with their maximum delivery time in hours.
 *
 * The agent uses maxHours to filter corridors:
 * - INSTANT requested → only corridors with typicalHours <= 1 are eligible
 * - STANDARD requested → all corridors are eligible (72h covers everything)
 *
 * The maxHours field is queried in CorridorComparator.isEligible() to filter
 * out corridors that cannot meet the user's urgency requirement.
 */
public enum Urgency {

    /** Delivery expected within 1 hour. Only UPI and CRYPTO_USDT qualify. */
    INSTANT(1),

    /** Delivery expected within 24 hours. UPI, CRYPTO_USDT, and WISE qualify. */
    SAME_DAY(24),

    /** Delivery expected within 72 hours. All corridors qualify. */
    STANDARD(72);

    private final int maxHours;

    Urgency(int maxHours) {
        this.maxHours = maxHours;
    }

    public int getMaxHours() {
        return maxHours;
    }
}