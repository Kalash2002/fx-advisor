-- ─────────────────────────────────────────────────────────────
-- TABLE: refresh_tokens
-- Stores server-side refresh tokens for JWT rotation.
-- token: UUID v4 string — random, opaque, unpredictable
-- rotated_to: if this token was already used to refresh, this field
--   contains the UUID of the replacement. NULL = not yet used.
--   Non-NULL + presented again = REPLAY ATTACK → revoke all sessions
-- device_info: optional user-agent or device fingerprint for UX
--   ("Your session on Chrome/Mac was revoked")
-- ─────────────────────────────────────────────────────────────
CREATE TABLE refresh_tokens (
                                id          BIGINT          NOT NULL AUTO_INCREMENT,
                                token       VARCHAR(36)     NOT NULL,   -- UUID v4: 32 hex chars + 4 hyphens = 36
                                user_id     BIGINT          NOT NULL,
                                expires_at  DATETIME(6)     NOT NULL,
                                revoked     TINYINT(1)      NOT NULL DEFAULT 0,
                                revoked_at  DATETIME(6),
                                rotated_to  VARCHAR(36),               -- UUID of the replacement token
                                device_info VARCHAR(255),
                                created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                PRIMARY KEY (id),
                                UNIQUE KEY uk_refresh_token (token),
                                CONSTRAINT fk_rt_user FOREIGN KEY (user_id)
                                    REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- CRITICAL index: every auth request validates a refresh token by its
-- value. Without this index, validation = full table scan on millions of rows.
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token   ON refresh_tokens(token);

-- Q. Why is UNIQUE KEY on token not enough — why also a separate index?
-- UNIQUE KEY creates a unique B-tree index. The separate index on user_id
-- is for the "revoke all tokens for user" operation (revokeAllForUser(userId))
-- which does WHERE user_id = ?. Without the user_id index, this is a
-- full scan. Combined: lookup by token = fast, lookup all by user = fast.