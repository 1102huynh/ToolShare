package com.toolshare.payment.application;

public record ProviderResult(boolean success, String failureReason) {
    public static ProviderResult ok() {
        return new ProviderResult(true, null);
    }

    public static ProviderResult failure(String failureReason) {
        return new ProviderResult(false, failureReason);
    }
}
