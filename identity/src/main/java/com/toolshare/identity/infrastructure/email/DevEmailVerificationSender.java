package com.toolshare.identity.infrastructure.email;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class DevEmailVerificationSender implements EmailVerificationSender {
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String sendVerificationEmail(String emailAddress) {
        byte[] randomBytes = new byte[24];
        RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
