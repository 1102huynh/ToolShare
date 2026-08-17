package com.toolshare.payment.infrastructure.persistence;

import com.toolshare.payment.domain.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<PaymentOrder, UUID> {
    Optional<PaymentOrder> findByBookingId(UUID bookingId);

    Optional<PaymentOrder> findByProviderReference(String providerReference);
}
