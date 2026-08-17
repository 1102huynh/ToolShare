package com.toolshare.payment.infrastructure.provider;

import com.toolshare.payment.application.PaymentProvider;
import com.toolshare.payment.application.ProviderIntentResult;
import com.toolshare.payment.application.ProviderResult;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

/**
 * Deterministic, synchronous, in-process MVP {@link PaymentProvider} implementation.
 * Always succeeds and fabricates a UUID-derived provider reference. This is a
 * placeholder standing in for a real payment gateway integration (VNPay/Momo/
 * Stripe/etc.) and is explicitly NOT production-ready — no real network call, no
 * signature handling. Mirrors the interim/no-op adapter precedent already
 * established by {@code com.toolshare.availability.application.Phase2ANoBookingConflictProvider}.
 */
@Component
public class MvpPaymentProviderAdapter implements PaymentProvider {

    @Override
    public String providerName() {
        return "MVP";
    }

    @Override
    public ProviderIntentResult createIntent(UUID paymentId, long amountMinor, String currency) {
        Objects.requireNonNull(paymentId, "paymentId");
        return ProviderIntentResult.ok("mvp-" + paymentId);
    }

    @Override
    public ProviderResult authorize(String providerReference) {
        Objects.requireNonNull(providerReference, "providerReference");
        return ProviderResult.ok();
    }

    @Override
    public ProviderResult capture(String providerReference) {
        Objects.requireNonNull(providerReference, "providerReference");
        return ProviderResult.ok();
    }

    @Override
    public ProviderResult cancel(String providerReference) {
        Objects.requireNonNull(providerReference, "providerReference");
        return ProviderResult.ok();
    }
}
