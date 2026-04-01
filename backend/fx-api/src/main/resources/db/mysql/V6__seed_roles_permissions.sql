-- ─────────────────────────────────────────────────────────────
-- SEED: Permissions
-- Each permission maps to a Spring Security GrantedAuthority.
-- In JwtAuthFilter, permissions are loaded from the JWT claims.
-- In @PreAuthorize("hasAuthority('PERMISSION_ANALYSE')"), Spring
-- checks if this string is in the user's GrantedAuthority list.
-- ─────────────────────────────────────────────────────────────
INSERT INTO permissions (name, description) VALUES
('PERMISSION_ANALYSE',         'Can submit transfer analysis requests to the AI advisor'),
('PERMISSION_AUDIT_VIEW_OWN',  'Can view own transfer audit history'),
('PERMISSION_AUDIT_VIEW_ALL',  'Can view ALL users audit history — admin/analyst only'),
('PERMISSION_ADMIN',           'Full administrative access to all endpoints'),
('PERMISSION_INGEST',          'Can trigger PDF document ingestion into the vector store'),
('PERMISSION_CORRIDOR_MANAGE', 'Can update corridor fee/spread config in DB');

-- ─────────────────────────────────────────────────────────────
-- SEED: Roles
-- Three roles cover the typical fintech team structure:
-- ROLE_USER: end-users sending money
-- ROLE_ANALYST: compliance/ops team who review transfer logs
-- ROLE_ADMIN: engineering/product who manage config
-- ─────────────────────────────────────────────────────────────
INSERT INTO roles (name, description) VALUES
('ROLE_USER',     'Standard user — can analyse transfers and view own history'),
('ROLE_ANALYST',  'Compliance analyst — can view all transfer audit logs'),
('ROLE_ADMIN',    'System administrator — full access including config management');

-- ─────────────────────────────────────────────────────────────
-- SEED: Role → Permission mappings
-- Using SELECT subquery so we don't hardcode IDs (IDs are
-- AUTO_INCREMENT and not predictable across environments).
-- This pattern works regardless of what ID values were assigned.
-- ─────────────────────────────────────────────────────────────

-- ROLE_USER: can analyse transfers and view their own history only
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_USER'
  AND p.name IN ('PERMISSION_ANALYSE', 'PERMISSION_AUDIT_VIEW_OWN');

-- ROLE_ANALYST: everything USER gets, plus ability to view all transfers
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_ANALYST'
  AND p.name IN (
                 'PERMISSION_ANALYSE',
                 'PERMISSION_AUDIT_VIEW_OWN',
                 'PERMISSION_AUDIT_VIEW_ALL'
    );

-- ROLE_ADMIN: all permissions — every entry in the permissions table
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN';

-- ─────────────────────────────────────────────────────────────
-- SEED: Corridor Config
-- Initial values for the 4 transfer corridors.
-- These can be updated via admin API without code changes.
-- FeeEngine reads these values (with 5-min Redis cache).
-- ─────────────────────────────────────────────────────────────
INSERT INTO corridor_config
(corridor_type, spread_pct, flat_fee_usd, pct_fee, typical_hours,
 needs_compliance_check, display_name, description)
VALUES
    -- SWIFT: Traditional bank wire. Regulated, global coverage.
    -- High spread (1.5%) + flat $25 fee makes it expensive for small amounts.
    -- Needs compliance check: RBI has annual limits on SWIFT remittances.
    ('SWIFT',       1.500, 25.00, 0.000, 72, 1,
     'SWIFT Wire Transfer',
     'Traditional bank wire. Global coverage, 1-3 business days. Higher fees.'),

    -- UPI: India Unified Payments Interface. Near-instant, zero fees.
    -- Only works for INR destination. No compliance check needed for small amounts.
    ('UPI',         0.000,  0.00, 0.000,  1, 0,
     'UPI (India Instant)',
     'Instant transfer to Indian bank accounts via UPI. Zero fees.'),

    -- CRYPTO_USDT: Stablecoin rail. Fast, low fees but regulatory risk.
    -- RBI has restrictions on crypto remittances — always needs compliance check.
    ('CRYPTO_USDT', 0.200,  2.00, 0.100,  1, 1,
     'Crypto USDT Rail',
     'Stablecoin transfer via USDT. Fast and cheap but regulatory checks required.'),

    -- WISE: FinTech rail. Transparent fees, good rates, moderate speed.
    -- No compliance check needed — Wise handles KYC/AML independently.
    ('WISE',        0.500,  5.00, 0.450, 24, 0,
     'Wise Transfer',
     'Wise (formerly TransferWise). Transparent fees, real mid-market rate.');

-- ─────────────────────────────────────────────────────────────
-- SEED: Default Admin User
-- Password: Admin@123 (BCrypt hash with strength 10)
-- This is for development only. In production, the first admin
-- account is created via a secure onboarding process, not seeding.
-- BCrypt hash: $2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.
--
-- Q: How does BCrypt work?
-- BCrypt hashes are self-contained: $2a$10$<22-char-salt><31-char-hash>
-- The $10$ is the cost factor (2^10 = 1024 rounds of key derivation).
-- BCryptPasswordEncoder.matches(raw, stored) re-hashes the raw password
-- with the embedded salt and compares — no need to store the salt separately.
-- ─────────────────────────────────────────────────────────────
INSERT INTO users (email, password_hash, full_name, kyc_status, enabled)
VALUES (
           'admin@fxadvisor.com',
           '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.',
           'System Admin',
           'VERIFIED',
           1
       );

-- Assign ROLE_ADMIN to the admin user
-- Using SELECT subquery — avoids hardcoded IDs
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.email = 'admin@fxadvisor.com'
  AND r.name = 'ROLE_ADMIN';

-- Q: Why does admin get ROLE_ADMIN AND a user_roles entry?
-- roles table defines what roles EXIST. user_roles is the junction table
-- that ASSIGNS a role to a specific user. A role can exist in the system
-- without being assigned to anyone. We need both: the role definition
-- (from V1) and the assignment (this INSERT).

