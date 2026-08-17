package com.toolshare.payment.application;

public record ProviderIntentResult(boolean success, String providerReference, String failureReason) {
    public static ProviderIntentResult ok(String providerReference) {
        return new ProviderIntentResult(true, providerReference, null);
    }

    public static ProviderIntentResult failure(String failureReason) {
        return new ProviderIntentResult(false, null, failureReason);
    }
}
