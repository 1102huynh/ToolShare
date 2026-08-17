package com.toolshare.deposit.infrastructure.persistence;

import com.toolshare.deposit.domain.DepositTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DepositTransactionRepository extends JpaRepository<DepositTransaction, UUID> {
    List<DepositTransaction> findByDepositIdOrderByOccurredAtAsc(UUID depositId);
}
