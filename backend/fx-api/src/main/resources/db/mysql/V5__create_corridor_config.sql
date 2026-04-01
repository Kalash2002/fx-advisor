-- ─────────────────────────────────────────────────────────────
-- TABLE: corridor_config
-- DB-driven configuration for money transfer corridors.
-- Replaces hardcoded fee/spread values in the CorridorType enum.
-- FeeEngine reads from this table (via CorridorConfigService with cache).
--
-- This is the "Externalized Configuration" pattern:
-- Business rules (fees, spreads) live in the DB, not in code.
-- Changes take effect immediately without redeployment.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE corridor_config (
   id              BIGINT          NOT NULL AUTO_INCREMENT,
   corridor_type   ENUM('SWIFT','UPI','CRYPTO_USDT','WISE') NOT NULL,
   spread_pct      DECIMAL(5,3)    NOT NULL,   -- e.g. 1.500 means 1.5%
   flat_fee_usd    DECIMAL(10,2)   NOT NULL,   -- flat fee in USD e.g. 25.00
   pct_fee         DECIMAL(5,3)    NOT NULL,   -- percentage fee e.g. 0.450 = 0.45%
   typical_hours   INT             NOT NULL,   -- estimated delivery hours

    -- needs_compliance_check: if TRUE, the ReAct agent will call the RAG
    -- service (pgvector) to retrieve RBI/FEMA rules before recommending
    -- this corridor. SWIFT and CRYPTO_USDT require compliance checks.
   needs_compliance_check TINYINT(1) NOT NULL DEFAULT 0,
   is_active       TINYINT(1)      NOT NULL DEFAULT 1,
   display_name    VARCHAR(100),
   description     VARCHAR(500),

    -- Audit columns: who last changed this config and when
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by      BIGINT,   -- user_id of admin who last modified this row
    PRIMARY KEY (id),

    -- One active config row per corridor type — enforced at DB level
    -- without this, two SWIFT rows could exist and the app would be confused
    UNIQUE KEY uk_corridor_type (corridor_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Q: Why UNIQUE KEY on corridor_type and not just trust the app?
-- Defense in depth. Never trust the application layer alone to enforce
-- uniqueness. If a bug allows two INSERT statements to race (two admin
-- requests at the same moment), the DB constraint is the final guard.
-- The app will get a DuplicateKeyException which is catchable and expected.