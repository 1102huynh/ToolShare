package com.toolshare.payment;

import com.toolshare.payment.application.PaymentProvider;
import com.toolshare.payment.application.ProviderIntentResult;
import com.toolshare.payment.application.ProviderResult;

import java.util.UUID;

/**
 * Hand-written test double — no mocking framework is used anywhere in this
 * codebase (confirmed by inspection before writing this class), so this
 * mirrors that convention instead of introducing Mockito. Lets individual
 * test methods force any single {@link PaymentProvider} operation to report
 * failure, to exercise {@code PaymentService}'s provider-failure paths.
 */
class ConfigurablePaymentProvider implements PaymentProvider {

    private boolean failCreateIntent;
    private boolean failAuthorize;
    private boolean failCapture;
    private boolean failCancel;

    void failCreateIntent() {
        this.failCreateIntent = true;
    }

    void failAuthorize() {
        this.failAuthorize = true;
    }

    void failCapture() {
        this.failCapture = true;
    }

    void failCancel() {
        this.failCancel = true;
    }

    /**
     * This is a Spring-managed singleton bean, so its state otherwise leaks
     * across test methods within the same test class run. Must be called from
     * a {@code @BeforeEach} in every test class that uses it.
     */
    void reset() {
        this.failCreateIntent = false;
        this.failAuthorize = false;
        this.failCapture = false;
        this.failCancel = false;
    }

    @Override
    public String providerName() {
        return "TEST-CONFIGURABLE";
    }

    @Override
    public ProviderIntentResult createIntent(UUID paymentId, long amountMinor, String currency) {
        return failCreateIntent
                ? ProviderIntentResult.failure("forced test failure")
                : ProviderIntentResult.ok("test-" + paymentId);
    }

    @Override
    public ProviderResult authorize(String providerReference) {
        return failAuthorize ? ProviderResult.failure("forced test failure") : ProviderResult.ok();
    }

    @Override
    public ProviderResult capture(String providerReference) {
        return failCapture ? ProviderResult.failure("forced test failure") : ProviderResult.ok();
    }

    @Override
    public ProviderResult cancel(String providerReference) {
        return failCancel ? ProviderResult.failure("forced test failure") : ProviderResult.ok();
    }
}
