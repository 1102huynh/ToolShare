package com.toolshare.payment.api;

import com.toolshare.payment.application.PaymentWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * First controller in {@code payment} (T-012 plan §12). Not behind identity's JWT
 * filter chain — the caller is an external payment gateway, not a logged-in user;
 * its trust boundary is entirely the signature check inside
 * {@link PaymentWebhookService}. The raw body is bound as a plain {@code String}
 * (no automatic JSON binding) so signature verification runs over the exact bytes
 * received, before any parsing — see the plan's §6/§7.
 */
@RestController
@RequestMapping("/api/v1/payments/webhooks")
public class PaymentWebhookController {

    private static final String SIGNATURE_HEADER = "X-Webhook-Signature";

    private final PaymentWebhookService paymentWebhookService;

    public PaymentWebhookController(PaymentWebhookService paymentWebhookService) {
        this.paymentWebhookService = paymentWebhookService;
    }

    @PostMapping("/{providerName}")
    public ResponseEntity<Void> receive(
            @PathVariable String providerName,
            @RequestBody String rawBody,
            @RequestHeader(name = SIGNATURE_HEADER, required = false) String signatureHeader
    ) {
        paymentWebhookService.processWebhook(providerName, rawBody, signatureHeader);
        return ResponseEntity.ok().build();
    }
}
