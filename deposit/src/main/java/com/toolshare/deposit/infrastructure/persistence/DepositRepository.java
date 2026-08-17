package com.toolshare.deposit.infrastructure.persistence;

import com.toolshare.deposit.domain.DepositCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DepositRepository extends JpaRepository<DepositCase, UUID> {
    Optional<DepositCase> findByBookingId(UUID bookingId);
}
