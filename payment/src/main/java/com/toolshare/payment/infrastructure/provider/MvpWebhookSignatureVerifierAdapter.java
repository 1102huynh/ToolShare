package com.toolshare.payment.infrastructure.provider;

import com.toolshare.payment.application.WebhookSecurityProperties;
import com.toolshare.payment.application.WebhookSignatureVerifier;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * MVP {@link WebhookSignatureVerifier}: HMAC-SHA256 over the raw payload, keyed by
 * a per-provider secret from {@link WebhookSecurityProperties}. Expected header
 * format is {@code sha256=<hex>} — an invented-but-conventional scheme (mirrors
 * several real gateways), not copied from any specific named provider. Comparison
 * is constant-time ({@link MessageDigest#isEqual}) to avoid a timing side-channel;
 * this is a security-load-bearing detail, not a style choice.
 */
@Component
public class MvpWebhookSignatureVerifierAdapter implements WebhookSignatureVerifier {

    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final WebhookSecurityProperties properties;

    public MvpWebhookSignatureVerifierAdapter(WebhookSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean verify(String providerName, String rawPayload, String signatureHeader) {
        if (providerName == null || rawPayload == null || signatureHeader == null) {
            return false;
        }
        if (!signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }
        String secret = properties.getSecrets().get(providerName);
        if (secret == null || secret.isBlank()) {
            return false;
        }

        byte[] provided;
        try {
            provided = HexFormat.of().parseHex(signatureHeader.substring(SIGNATURE_PREFIX.length()));
        } catch (IllegalArgumentException ex) {
            return false;
        }

        byte[] expected = computeHmac(secret, rawPayload);
        return MessageDigest.isEqual(expected, provided);
    }

    private byte[] computeHmac(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("Unable to compute webhook HMAC signature", ex);
        }
    }
}
