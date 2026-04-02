package com.fxadvisor.core.enums;

/**
 * Supported transfer corridor types.
 *
 * DESIGN DECISION: This enum contains ONLY names — no fee data, no spread percentages,
 * no typical hours. All corridor configuration is stored in the MySQL corridor_config table
 * and loaded at runtime via CorridorConfigService. This allows fee adjustments without
 * redeploying the application.
 *
 * The 4 corridors map to real-world transfer rails:
 * - SWIFT:        Traditional bank wire (SWIFT network, 72h)
 * - UPI:          Unified Payments Interface — India-specific instant rail (1h)
 * - CRYPTO_USDT:  USDT stablecoin on-chain transfer (Tron/ERC-20)
 * - WISE:         Wise (formerly TransferWise) — multi-currency account rail
 */
public enum CorridorType {
    SWIFT,
    UPI,
    CRYPTO_USDT,
    WISE
}