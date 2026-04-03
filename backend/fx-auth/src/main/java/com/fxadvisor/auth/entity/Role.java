package com.fxadvisor.auth.entity;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

/**
 * JPA entity for the `roles` table.
 *
 * Roles have a many-to-many relationship with permissions via `role_permissions`.
 * FetchType.EAGER is intentional — permissions are always needed when loading
 * a user's authorities for the JWT claims. Lazy loading would cause
 * LazyInitializationException outside of a transaction (in filter chain).
 *
 * Role names follow Spring Security convention: "ROLE_USER", "ROLE_ADMIN", "ROLE_ANALYST"
 */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;  // "ROLE_USER", "ROLE_ADMIN", "ROLE_ANALYST"

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<Permission> permissions = new HashSet<>();

    protected Role() {}

    public Role(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Set<Permission> getPermissions() { return permissions; }

    @Override
    public String toString() {
        return "Role{name='" + name + "'}";
    }
}