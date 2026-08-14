package com.toolshare.identity.domain;

import com.toolshare.identity.authorization.Permission;
import com.toolshare.identity.authorization.Role;
import com.toolshare.identity.authorization.RolePermissionMatrix;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "identity_accounts",
        indexes = {
                @Index(name = "idx_identity_account_email_normalized", columnList = "email", unique = true)
        }
)
public class IdentityAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountState state;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "identity_account_roles",
            joinColumns = @JoinColumn(name = "identity_account_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    protected IdentityAccount() {
    }

    public IdentityAccount(String email, String passwordHash) {
        this.email = normalizeEmail(email);
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.state = AccountState.PENDING_VERIFICATION;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static IdentityAccount register(String email, String passwordHash) {
        return new IdentityAccount(email, passwordHash);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public AccountState getState() {
        return state;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public OffsetDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.updatedAt = OffsetDateTime.now();
    }

    public void markEmailVerified() {
        this.verifiedAt = OffsetDateTime.now();
        this.updatedAt = this.verifiedAt;
    }

    public void activate() {
        if (this.state == AccountState.ACTIVE) {
            throw new IllegalStateException("Account is already active");
        }
        if (this.state != AccountState.PENDING_VERIFICATION) {
            throw new IllegalStateException("Account must be pending verification before activation");
        }
        if (verifiedAt == null) {
            throw new IllegalStateException("Account must be email verified before activation");
        }
        this.state = AccountState.ACTIVE;
        this.updatedAt = OffsetDateTime.now();
    }

    public void suspend() {
        if (this.state == AccountState.SUSPENDED) {
            throw new IllegalStateException("Account is already suspended");
        }
        if (this.state == AccountState.DEACTIVATED) {
            throw new IllegalStateException("Deactivated account cannot be suspended");
        }
        this.state = AccountState.SUSPENDED;
        this.updatedAt = OffsetDateTime.now();
    }

    public void deactivate() {
        if (this.state == AccountState.DEACTIVATED) {
            throw new IllegalStateException("Account is already deactivated");
        }
        this.state = AccountState.DEACTIVATED;
        this.updatedAt = OffsetDateTime.now();
    }

    public void recordLogin() {
        this.lastLoginAt = OffsetDateTime.now();
        this.updatedAt = this.lastLoginAt;
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    public void addRole(Role role) {
        if (role == null) {
            throw new IllegalArgumentException("Role is required");
        }
        roles.add(role);
        this.updatedAt = OffsetDateTime.now();
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public boolean hasPermission(Permission permission) {
        return RolePermissionMatrix.hasPermission(roles, permission);
    }

    public boolean canLogin() {
        return state == AccountState.ACTIVE && verifiedAt != null;
    }

    public static String normalizeEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException("Email is required");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        return normalized;
    }
}
