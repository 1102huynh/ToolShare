package com.toolshare.identity;

import com.toolshare.identity.application.IdentityAuthenticationService;
import com.toolshare.identity.domain.AccountState;
import com.toolshare.identity.domain.IdentityAccount;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdentityAuthenticationServiceTest {

    @Test
    void duplicate_email_is_rejected() {
        IdentityAuthenticationService service = new IdentityAuthenticationService();
        service.register("dup@example.com", "StrongPass!123");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.register("dup@example.com", "AnotherPass!456")
        );

        assertTrue(exception.getMessage().contains("already"));
    }

    @Test
    void unverified_account_cannot_login() {
        IdentityAuthenticationService service = new IdentityAuthenticationService();
        service.register("pending@example.com", "StrongPass!123");

        IdentityAccount account = service.findAccount("pending@example.com");
        assertEquals(AccountState.PENDING_VERIFICATION, account.getState());
        assertFalse(service.canAuthenticate(account));
    }
}
