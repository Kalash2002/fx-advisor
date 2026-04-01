-- ─────────────────────────────────────────────────────────────
-- TABLE: session_metadata
-- Tracks FX advisor conversation sessions at the MySQL level.
-- Complements Redis conversation history (which has TTL and auto-expires).
-- MySQL gives us: admin visibility, user history queries, analytics.
-- Redis gives us: fast message storage, auto-cleanup of old sessions.
-- Both are needed — they serve different purposes.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE session_metadata (
                                  id              BIGINT          NOT NULL AUTO_INCREMENT,
                                  session_id      VARCHAR(36)     NOT NULL,   -- UUID matching the Redis key
                                  user_id         BIGINT          NOT NULL,
                                  started_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                  last_active_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                  message_count   INT             NOT NULL DEFAULT 0,
                                  is_active       TINYINT(1)      NOT NULL DEFAULT 1,
                                  terminated_at   DATETIME(6),               -- NULL = still active
                                  PRIMARY KEY (id),
                                  UNIQUE KEY uk_session_id (session_id),
                                  CONSTRAINT fk_sm_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_session_meta_user_id ON session_metadata(user_id);

-- Composite index: admin queries like "find all active sessions idle for > 30min"
-- do: WHERE is_active = 1 AND last_active_at < ?
-- This index covers both columns in the WHERE clause simultaneously.
CREATE INDEX idx_session_meta_active  ON session_metadata(is_active, last_active_at);