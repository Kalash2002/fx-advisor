package com.fxadvisor.core.enums;

/**
 * Application-level permissions for RBAC.
 *
 * DESIGN DECISION: Permission names here MUST exactly match the strings stored
 * in the MySQL `permissions` table (seeded in V6__seed_roles_permissions.sql).
 * Spring Security loads permissions as GrantedAuthority from JWT claims,
 * and @PreAuthorize("hasAuthority('PERMISSION_ANALYSE')") does a string comparison.
 * A mismatch between this enum and the DB seeds will silently deny all requests.
 *
 * Role → Permission mapping (from V6 migration seed):
 * - ROLE_USER:     PERMISSION_ANALYSE, PERMISSION_AUDIT_VIEW_OWN
 * - ROLE_ADMIN:    all permissions
 * - ROLE_ANALYST:  PERMISSION_AUDIT_VIEW_ALL, PERMISSION_AUDIT_VIEW_OWN
 */
public enum Permission {

    /** Can call POST /api/v1/advisor/analyse */
    PERMISSION_ANALYSE,

    /** Can view own transfer audit history */
    PERMISSION_AUDIT_VIEW_OWN,

    /** Can view all users' audit history (analyst/admin only) */
    PERMISSION_AUDIT_VIEW_ALL,

    /** Full administrative access */
    PERMISSION_ADMIN,

    /** Can trigger compliance document ingestion */
    PERMISSION_INGEST,

    /** Can create/update/delete corridor configuration */
    PERMISSION_CORRIDOR_MANAGE
}