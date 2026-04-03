package com.fxadvisor.auth.entity;

import jakarta.persistence.*;

/**
 * JPA entity for the `permissions` table.
 *
 * The `name` column must exactly match values from the fx-core Permission enum.
 * Spring Security loads these as GrantedAuthority strings and compares them
 * to the string in @PreAuthorize("hasAuthority('PERMISSION_ANALYSE')").
 *
 * Seeded by V6__seed_roles_permissions.sql. Never created via JPA (ddl-auto: validate).
 */
@Entity
@Table(name = "permissions")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String name;  // e.g. "PERMISSION_ANALYSE"

    // JPA requires no-arg constructor
    protected Permission() {}

    public Permission(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public String getName() { return name; }

    @Override
    public String toString() {
        return "Permission{name='" + name + "'}";
    }
}