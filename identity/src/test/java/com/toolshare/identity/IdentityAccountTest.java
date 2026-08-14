package com.toolshare.identity;

import com.toolshare.identity.domain.AccountState;
import com.toolshare.identity.domain.IdentityAccount;
import com.toolshare.identity.domain.VerificationChallenge;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class IdentityAccountTest {

    @Test
    void registration_normalizes_email_and_marks_pending_verification() {
        IdentityAccount account = IdentityAccount.register("  User.Name+Tag@Example.com  ", "StrongPass!123");

        assertEquals("user.name+tag@example.com", account.getEmail());
        assertEquals(AccountState.PENDING_VERIFICATION, account.getState());
        assertNotNull(account.getPasswordHash());
        assertFalse(account.getPasswordHash().isBlank());
    }

    @Test
    void account_can_login_only_when_active_and_email_verified() {
        IdentityAccount account = IdentityAccount.register("user@example.com", "StrongPass!123");
        assertFalse(account.canLogin());

        account.markEmailVerified();
        account.activate();
        assertTrue(account.canLogin());
    }

    @Test
    void verification_challenge_rejects_expired_or_reused_tokens() {
        IdentityAccount account = IdentityAccount.register("user@example.com", "StrongPass!123");
        VerificationChallenge challenge = new VerificationChallenge(
                "abc123",
                account,
                OffsetDateTime.now().plusMinutes(10)
        );

        assertFalse(challenge.isExpired());
        assertFalse(challenge.isUsed());

        challenge.markUsed();
        assertTrue(challenge.isUsed());

        VerificationChallenge expired = new VerificationChallenge(
                "def456",
                account,
                OffsetDateTime.now().minusMinutes(1)
        );
        assertTrue(expired.isExpired());
    }

    @Test
    void activation_requires_verified_pending_account_state() {
        IdentityAccount account = IdentityAccount.register("user@example.com", "StrongPass!123");

        IllegalStateException exception = assertThrows(IllegalStateException.class, account::activate);
        assertTrue(exception.getMessage().contains("verified"));

        account.markEmailVerified();
        account.activate();
        assertEquals(AccountState.ACTIVE, account.getState());

        IdentityAccount suspended = IdentityAccount.register("member@example.com", "StrongPass!123");
        suspended.markEmailVerified();
        suspended.activate();
        suspended.suspend();

        IllegalStateException rejection = assertThrows(IllegalStateException.class, suspended::activate);
        assertTrue(rejection.getMessage().contains("pending"));
    }
}
