package com.toolshare.payment.application;

/**
 * Decouples {@link PaymentWebhookService} from any specific signature scheme,
 * mirroring the {@link PaymentProvider} port/adapter convention T-011 established.
 * Verification must run over the exact raw bytes received, before any JSON
 * parsing — see {@code PaymentWebhookController}.
 */
public interface WebhookSignatureVerifier {

    /**
     * @param providerName    the {@code {providerName}} path variable, used to resolve
     *                        the per-provider secret
     * @param rawPayload      the exact raw request body, unparsed
     * @param signatureHeader the raw {@code X-Webhook-Signature} header value
     * @return true only if the signature is present, well-formed, and matches the
     * HMAC computed over {@code rawPayload} with the configured secret for
     * {@code providerName}
     */
    boolean verify(String providerName, String rawPayload, String signatureHeader);
}
