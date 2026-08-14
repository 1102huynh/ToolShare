package com.toolshare.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "verification_challenges")
public class VerificationChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "identity_account_id", nullable = false)
    private IdentityAccount identityAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationPurpose purpose;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    protected VerificationChallenge() {
    }

    public VerificationChallenge(String tokenHash, IdentityAccount identityAccount, OffsetDateTime expiresAt) {
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.identityAccount = Objects.requireNonNull(identityAccount, "identityAccount");
        this.purpose = VerificationPurpose.EMAIL_VERIFICATION;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public IdentityAccount getIdentityAccount() {
        return identityAccount;
    }

    public VerificationPurpose getPurpose() {
        return purpose;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUsedAt() {
        return usedAt;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(OffsetDateTime.now());
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public void markUsed() {
        this.usedAt = OffsetDateTime.now();
    }
}
