package com.toolshare.payment.application;

import java.util.UUID;

/**
 * Decouples {@link PaymentService} from any specific payment gateway SDK
 * (ARCHITECTURE.md §7, ADR-004). Owned by the module that needs it, mirroring
 * the {@code BookingConflictProvider} port/adapter convention in
 * {@code availability.application}. This is a synchronous port only — signature
 * verification and webhook payload handling belong to T-012, not here.
 */
public interface PaymentProvider {

    /**
     * A short, stable identifier for this provider implementation, persisted on
     * {@code PaymentOrder.providerName} (e.g. "MVP"). Not part of the plan's
     * original four-method sketch; added because the port is the correct
     * ports/adapters place for a provider to self-describe, rather than
     * PaymentService hardcoding or type-checking a specific adapter.
     */
    String providerName();

    ProviderIntentResult createIntent(UUID paymentId, long amountMinor, String currency);

    ProviderResult authorize(String providerReference);

    ProviderResult capture(String providerReference);

    ProviderResult cancel(String providerReference);
}
