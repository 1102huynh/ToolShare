package com.toolshare.identity.infrastructure.persistence;

import com.toolshare.identity.domain.VerificationChallenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface VerificationChallengeRepository extends JpaRepository<VerificationChallenge, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select vc from VerificationChallenge vc where vc.tokenHash = :tokenHash")
    Optional<VerificationChallenge> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    Optional<VerificationChallenge> findByTokenHash(String tokenHash);
    Optional<VerificationChallenge> findByIdentityAccountIdAndPurpose(UUID identityAccountId, com.toolshare.identity.domain.VerificationPurpose purpose);
}
