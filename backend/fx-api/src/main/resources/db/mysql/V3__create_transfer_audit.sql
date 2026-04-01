-- ─────────────────────────────────────────────────────────────
-- TABLE: transfer_audit
-- Immutable log of every AI advisor analysis request.
-- Records what was asked, what was recommended, how long it took,
-- and how many AI tokens were consumed (for cost tracking).
-- This table is APPEND-ONLY — records are never updated or deleted.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE transfer_audit (
                                id                   BIGINT          NOT NULL AUTO_INCREMENT,
                                session_id           VARCHAR(36)     NOT NULL,   -- Redis session UUID
                                user_id              BIGINT          NOT NULL,
                                source_currency      CHAR(3)         NOT NULL,   -- ISO 4217: USD, EUR, GBP
                                target_currency      CHAR(3)         NOT NULL,   -- ISO 4217: INR, PHP, MXN
                                amount               DECIMAL(20,4)   NOT NULL,   -- BigDecimal precision

    -- INTERVIEW: Why DECIMAL(20,4) not DOUBLE for money?
    -- DOUBLE is a floating-point type. 0.1 + 0.2 = 0.30000000000000004 in
    -- IEEE 754 floating point. For financial values this is unacceptable.
    -- DECIMAL(20,4) is exact base-10 storage: up to 16 digits before decimal,
    -- 4 after. MySQL stores it as a string internally — no floating point error.

                                urgency              ENUM('INSTANT','SAME_DAY','STANDARD') NOT NULL,
                                recommended_corridor VARCHAR(20),               -- SWIFT|UPI|CRYPTO_USDT|WISE
                                mid_market_rate      DECIMAL(20,8),             -- 8 decimal places for FX rates
                                effective_rate       DECIMAL(20,8),
                                total_fee_usd        DECIMAL(10,4),
                                received_amount      DECIMAL(20,4),
                                ai_tokens_used       INT,                       -- Total tokens consumed (prompt + completion)
                                latency_ms           INT,                       -- End-to-end request latency in ms
                                status               ENUM('COMPLETED','ERROR','PARTIAL') NOT NULL DEFAULT 'COMPLETED',
                                created_at           DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                PRIMARY KEY (id),
                                CONSTRAINT fk_ta_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Indexes covering the most common query patterns:
-- "Show my transfer history" → filter by user_id
-- "Show transfers in date range" → filter by created_at
-- "Find this specific session" → filter by session_id
CREATE INDEX idx_transfer_audit_user_id    ON transfer_audit(user_id);
CREATE INDEX idx_transfer_audit_session_id ON transfer_audit(session_id);
CREATE INDEX idx_transfer_audit_created_at ON transfer_audit(created_at);

-- INTERVIEW: Why index created_at?
-- Reports like "all transfers this month" do: WHERE created_at BETWEEN ? AND ?
-- Without the index this scans every row. With a B-tree index on created_at,
-- MySQL can seek directly to the date range. Critical for compliance reports.