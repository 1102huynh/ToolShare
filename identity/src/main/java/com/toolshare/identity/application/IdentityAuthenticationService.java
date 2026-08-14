package com.toolshare.identity.application;

import com.toolshare.identity.domain.IdentityAccount;
import com.toolshare.identity.domain.AccountState;

import java.util.HashMap;
import java.util.Map;

public class IdentityAuthenticationService {
    private final Map<String, IdentityAccount> accounts = new HashMap<>();

    public IdentityAccount register(String email, String password) {
        String normalizedEmail = IdentityAccount.normalizeEmail(email);
        if (accounts.containsKey(normalizedEmail)) {
            throw new IllegalArgumentException("Account with this email already exists");
        }
        IdentityAccount account = IdentityAccount.register(normalizedEmail, password);
        accounts.put(normalizedEmail, account);
        return account;
    }

    public IdentityAccount findAccount(String email) {
        return accounts.get(IdentityAccount.normalizeEmail(email));
    }

    public boolean canAuthenticate(IdentityAccount account) {
        return account != null && account.getState() == AccountState.ACTIVE && account.getVerifiedAt() != null;
    }
}
