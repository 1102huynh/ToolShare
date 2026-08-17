package com.toolshare.payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Shared test helper computing valid {@code sha256=<hex>} HMAC signatures
 * (T-012 plan §7), avoiding duplicated HMAC-construction code across
 * {@link PaymentWebhookServiceTest} and {@link PaymentWebhookControllerIntegrationTest}.
 * {@link #TEST_SECRET} matches the "MVP" placeholder secret configured in
 * {@code payment/src/main/resources/application.yml}.
 */
final class WebhookTestSigning {

    static final String TEST_PROVIDER = "MVP";
    static final String TEST_SECRET = "local-dev-only-placeholder-secret";

    private WebhookTestSigning() {
    }

    static String sign(String payload) {
        return sign(payload, TEST_SECRET);
    }

    static String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("Unable to compute test HMAC signature", ex);
        }
    }
}
