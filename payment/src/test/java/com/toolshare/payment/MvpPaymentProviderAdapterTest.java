package com.toolshare.payment;

import com.toolshare.payment.application.ProviderIntentResult;
import com.toolshare.payment.application.ProviderResult;
import com.toolshare.payment.infrastructure.provider.MvpPaymentProviderAdapter;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain JUnit unit test (no {@code @SpringBootTest}) per the plan's §16 revision
 * note: {@link MvpPaymentProviderAdapter} has no Spring-managed dependencies, so a
 * full application context adds no coverage value.
 */
class MvpPaymentProviderAdapterTest {

    private final MvpPaymentProviderAdapter adapter = new MvpPaymentProviderAdapter();

    @Test
    void provider_name_identifies_the_mvp_adapter() {
        assertEquals("MVP", adapter.providerName());
    }

    @Test
    void create_intent_always_succeeds_with_a_deterministic_reference() {
        UUID paymentId = UUID.randomUUID();

        ProviderIntentResult result = adapter.createIntent(paymentId, 100_000L, "VND");

        assertTrue(result.success());
        assertNotNull(result.providerReference());
        assertTrue(result.providerReference().contains(paymentId.toString()));
    }

    @Test
    void authorize_capture_and_cancel_always_succeed() {
        String reference = "mvp-" + UUID.randomUUID();

        ProviderResult authorize = adapter.authorize(reference);
        ProviderResult capture = adapter.capture(reference);
        ProviderResult cancel = adapter.cancel(reference);

        assertTrue(authorize.success());
        assertTrue(capture.success());
        assertTrue(cancel.success());
    }
}
