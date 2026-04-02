package com.fxadvisor.core.domain;

import com.fxadvisor.core.enums.CorridorType;

import java.math.BigDecimal;

/**
 * Domain object representing the runtime configuration for one transfer corridor.
 *
 * This is the domain model mapped FROM the MySQL corridor_config table row.
 * It is NOT a JPA entity (that lives in fx-corridor). It is the pure domain
 * representation passed between layers.
 *
 * CorridorConfigService (fx-corridor) loads these from DB, caches in Redis for 5min,
 * and provides them to FeeEngine for calculation.
 *
 * Fee calculation formula (implemented in FeeEngine):
 *   midMarketRate    = from Frankfurter API (e.g., 83.42 for USD/INR)
 *   effectiveRate    = midMarketRate * (1 - spreadPct/100)
 *   grossAmount      = transferAmount * effectiveRate
 *   totalFeeInTarget = (flatFeeUsd * effectiveRate) + (grossAmount * pctFee/100)
 *   receivedAmount   = grossAmount - totalFeeInTarget
 *
 * @param corridorType          The corridor this config applies to
 * @param spreadPct             FX spread margin in percent (e.g., 1.5 = 1.5%)
 * @param flatFeeUsd            Fixed fee in USD (converted to target currency at effective rate)
 * @param pctFee                Percentage fee on the transfer amount (e.g., 0.45 = 0.45%)
 * @param typicalHours          Expected delivery time in hours
 * @param needsComplianceCheck  Whether RAG compliance retrieval should be triggered
 * @param isActive              Whether this corridor is currently offered
 */
public record CorridorConfig(
        CorridorType corridorType,
        BigDecimal spreadPct,
        BigDecimal flatFeeUsd,
        BigDecimal pctFee,
        int typicalHours,
        boolean needsComplianceCheck,
        boolean isActive
) {
    public CorridorConfig {
        if (corridorType == null) {
            throw new IllegalArgumentException("corridorType must not be null");
        }
        if (spreadPct == null || spreadPct.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("spreadPct must be >= 0");
        }
        if (flatFeeUsd == null || flatFeeUsd.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("flatFeeUsd must be >= 0");
        }
        if (pctFee == null || pctFee.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("pctFee must be >= 0");
        }
        if (typicalHours <= 0) {
            throw new IllegalArgumentException("typicalHours must be positive");
        }
    }
}