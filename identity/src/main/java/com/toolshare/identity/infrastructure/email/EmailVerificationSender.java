package com.toolshare.identity.infrastructure.email;

public interface EmailVerificationSender {
    String sendVerificationEmail(String emailAddress);
}
