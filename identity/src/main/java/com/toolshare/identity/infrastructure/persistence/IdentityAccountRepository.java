package com.toolshare.identity.infrastructure.persistence;

import com.toolshare.identity.domain.IdentityAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdentityAccountRepository extends JpaRepository<IdentityAccount, UUID> {
    Optional<IdentityAccount> findByEmail(String email);
    boolean existsByEmail(String email);
}
