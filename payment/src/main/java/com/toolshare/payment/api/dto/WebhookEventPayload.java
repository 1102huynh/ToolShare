package com.toolshare.payment.api.dto;

import java.time.OffsetDateTime;

/**
 * MVP webhook wire format (T-012 plan §6) — invented for the fake provider, not
 * copied from any specific named gateway. Deliberately parsed manually via the
 * injected {@code ObjectMapper} inside {@code PaymentWebhookService}, not bound
 * automatically by Spring MVC, so signature verification (§7) can run over the
 * exact raw bytes first.
 */
public record WebhookEventPayload(
        String eventId,
        String eventType,
        String providerReference,
        OffsetDateTime occurredAt
) {
}
