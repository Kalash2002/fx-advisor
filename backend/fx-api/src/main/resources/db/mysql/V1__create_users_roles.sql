-- ─────────────────────────────────────────────────────────────
-- TABLE: users
-- Central identity table. Stores credentials and profile info.
-- email is UNIQUE — used as the username for login.
-- password_hash stores BCrypt output, never plain text.
-- kyc_status: Know Your Customer — regulatory requirement for
-- financial apps. Affects which corridors a user can access.
-- enabled: soft-disable without deleting the record.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE users (
                       id            BIGINT          NOT NULL AUTO_INCREMENT,
                       email         VARCHAR(255)    NOT NULL,
                       password_hash VARCHAR(255)    NOT NULL,
                       full_name     VARCHAR(255),
                       phone         VARCHAR(30),
                       country_code  CHAR(2),
                       kyc_status    ENUM('PENDING','VERIFIED','REJECTED') NOT NULL DEFAULT 'PENDING',
                       enabled       TINYINT(1)      NOT NULL DEFAULT 1,
                       created_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                       updated_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                  ON UPDATE CURRENT_TIMESTAMP(6),
                       PRIMARY KEY (id),
                       UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Q. Why utf8mb4 and not utf8?
-- MySQL's 'utf8' is actually a 3-byte subset of real UTF-8 and cannot
-- store emoji or some rare Unicode characters. utf8mb4 is full 4-byte
-- UTF-8. Always use utf8mb4 in modern MySQL schemas.

-- Q. Why DATETIME(6) instead of DATETIME?
-- DATETIME(6) stores microsecond precision. Standard DATETIME only
-- stores to the second. For audit logs and ordering, microsecond
-- precision prevents ties when two records are inserted in the same second.

-- ─────────────────────────────────────────────────────────────
-- TABLE: roles
-- e.g. ROLE_USER, ROLE_ADMIN, ROLE_ANALYST
-- Name follows Spring Security convention: ROLE_ prefix for roles.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE roles (
                       id          BIGINT          NOT NULL AUTO_INCREMENT,
                       name        VARCHAR(50)     NOT NULL,
                       description VARCHAR(255),
                       created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                       PRIMARY KEY (id),
                       UNIQUE KEY uk_roles_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE: permissions
-- Fine-grained permissions. e.g. PERMISSION_ANALYSE, PERMISSION_ADMIN
-- Roles aggregate permissions. Users get permissions through roles.
-- This is more flexible than checking role names in code because
-- you can add a new permission to a role without code changes.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE permissions (
                             id          BIGINT          NOT NULL AUTO_INCREMENT,
                             name        VARCHAR(100)    NOT NULL,
                             description VARCHAR(255),
                             PRIMARY KEY (id),
                             UNIQUE KEY uk_permissions_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────
-- TABLE: user_roles (join table — many-to-many)
-- Composite PK on (user_id, role_id) prevents duplicate assignments.
-- assigned_by tracks which admin made the assignment — audit trail.
-- CASCADE DELETE: if a user or role is deleted, their assignment rows
-- are automatically removed. Prevents orphaned records.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE user_roles (
                            user_id     BIGINT      NOT NULL,
                            role_id     BIGINT      NOT NULL,
                            assigned_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                            assigned_by BIGINT,
                            PRIMARY KEY (user_id, role_id),
                            CONSTRAINT fk_ur_user FOREIGN KEY (user_id)
                                REFERENCES users(id) ON DELETE CASCADE,
                            CONSTRAINT fk_ur_role FOREIGN KEY (role_id)
                                REFERENCES roles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ─────────────────────────────────────────────────────────────
-- TABLE: role_permissions (join table — many-to-many)
-- Maps which permissions each role grants.
-- Changing a role's permissions takes effect immediately for all
-- users of that role (next JWT issue / token refresh).
-- ─────────────────────────────────────────────────────────────
CREATE TABLE role_permissions (
                                  role_id       BIGINT  NOT NULL,
                                  permission_id BIGINT  NOT NULL,
                                  PRIMARY KEY (role_id, permission_id),
                                  CONSTRAINT fk_rp_role FOREIGN KEY (role_id)
                                      REFERENCES roles(id) ON DELETE CASCADE,
                                  CONSTRAINT fk_rp_perm FOREIGN KEY (permission_id)
                                      REFERENCES permissions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Indexes for JOIN performance on these lookup tables
-- Without indexes, loading a user's roles requires a full table scan
-- on user_roles. With index, it's a B-tree lookup by user_id.
CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_role_perms_role_id ON role_permissions(role_id);