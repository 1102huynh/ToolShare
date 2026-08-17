package com.toolshare.payment;

import com.toolshare.payment.application.WebhookSecurityProperties;
import com.toolshare.payment.infrastructure.provider.MvpWebhookSignatureVerifierAdapter;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain JUnit unit test (no {@code @SpringBootTest}) — mirrors
 * {@link MvpPaymentProviderAdapterTest}'s style, since
 * {@link MvpWebhookSignatureVerifierAdapter} has only a plain-object dependency.
 */
class WebhookSignatureVerifierTest {

    private static final String SECRET = "unit-test-secret";

    private final WebhookSecurityProperties properties = new WebhookSecurityProperties();
    private final MvpWebhookSignatureVerifierAdapter verifier;

    WebhookSignatureVerifierTest() {
        properties.setSecrets(Map.of("MVP", SECRET));
        this.verifier = new MvpWebhookSignatureVerifierAdapter(properties);
    }

    @Test
    void valid_signature_over_known_payload_and_secret_verifies_true() {
        String payload = "{\"eventId\":\"evt_1\"}";
        String signature = WebhookTestSigning.sign(payload, SECRET);

        assertTrue(verifier.verify("MVP", payload, signature));
    }

    @Test
    void tampered_payload_with_original_signature_verifies_false() {
        String payload = "{\"eventId\":\"evt_1\"}";
        String signature = WebhookTestSigning.sign(payload, SECRET);

        assertFalse(verifier.verify("MVP", payload + "-tampered", signature));
    }

    @Test
    void unknown_provider_name_verifies_false_because_no_secret_is_configured() {
        String payload = "{\"eventId\":\"evt_1\"}";
        String signature = WebhookTestSigning.sign(payload, SECRET);

        assertFalse(verifier.verify("UNKNOWN_PROVIDER", payload, signature));
    }

    @Test
    void malformed_signature_header_verifies_false_not_throws() {
        String payload = "{\"eventId\":\"evt_1\"}";

        assertFalse(verifier.verify("MVP", payload, "not-a-valid-signature-header"));
    }

    @Test
    void null_signature_header_verifies_false() {
        assertFalse(verifier.verify("MVP", "{}", null));
    }

    @Test
    void hex_that_does_not_decode_verifies_false_not_throws() {
        assertFalse(verifier.verify("MVP", "{}", "sha256=not-hex-zz"));
    }
}
