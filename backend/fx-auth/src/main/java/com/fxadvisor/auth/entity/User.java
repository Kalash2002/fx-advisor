package com.fxadvisor.auth.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * JPA entity for the `users` table.
 *
 * kycStatus: "PENDING" on registration, "VERIFIED" after admin approval.
 * enabled: false = soft-deleted or suspended user. JwtAuthFilter checks this
 * via UserDetails.isEnabled() and returns 401 if false.
 *
 * Roles are EAGER for the same reason as Role → permissions: always needed
 * for authority loading, and lazy would fail outside a transaction.
 *
 * NEVER return this entity directly from a controller — use DTOs.
 * The passwordHash field must never appear in any API response.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(name = "kyc_status", length = 20)
    private String kycStatus = "PENDING";

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    protected User() {}

    // Builder-style constructor for AuthService
    public User(String email, String passwordHash, String fullName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public String getKycStatus() { return kycStatus; }
    public boolean isEnabled() { return enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Set<Role> getRoles() { return roles; }

    public void addRole(Role role) { this.roles.add(role); }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}