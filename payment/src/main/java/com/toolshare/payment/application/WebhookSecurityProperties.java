package com.toolshare.payment.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Binds {@code toolshare.payment.webhook.*} from {@code application.yml}. The
 * MVP placeholder secret configured there is local-dev only, exactly like
 * {@code MvpPaymentProviderAdapter}'s own disclosed "not production-ready" scope —
 * a real deployment would source this from a secret manager instead.
 */
@Component
@ConfigurationProperties(prefix = "toolshare.payment.webhook")
public class WebhookSecurityProperties {

    /**
     * Per-provider HMAC secret, keyed by provider name (e.g. {@code "MVP"}).
     */
    private Map<String, String> secrets = new HashMap<>();

    /**
     * How old a webhook event's {@code occurredAt} may be before it is treated as
     * a stale/replayed delivery. Defaults to 5 minutes.
     */
    private Duration replayTolerance = Duration.ofMinutes(5);

    public Map<String, String> getSecrets() {
        return secrets;
    }

    public void setSecrets(Map<String, String> secrets) {
        this.secrets = secrets;
    }

    public Duration getReplayTolerance() {
        return replayTolerance;
    }

    public void setReplayTolerance(Duration replayTolerance) {
        this.replayTolerance = replayTolerance;
    }
}
