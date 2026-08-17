package com.toolshare.payment.infrastructure.persistence;

import com.toolshare.payment.domain.WebhookEventRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WebhookEventRepository extends JpaRepository<WebhookEventRecord, UUID> {
    Optional<WebhookEventRecord> findByProviderNameAndProviderEventId(String providerName, String providerEventId);
}
