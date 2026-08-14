package com.toolshare.listing.application;

import com.toolshare.common.exception.UnauthorizedException;
import com.toolshare.identity.domain.IdentityAccount;
import com.toolshare.identity.infrastructure.persistence.IdentityAccountRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedAccountResolver {

    private final IdentityAccountRepository identityAccountRepository;

    public AuthenticatedAccountResolver(IdentityAccountRepository identityAccountRepository) {
        this.identityAccountRepository = identityAccountRepository;
    }

    public IdentityAccount requireCurrentAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new UnauthorizedException("Authentication required");
        }

        String email = authentication.getName();
        return identityAccountRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Authentication required"));
    }
}