package com.toolshare.identity.infrastructure.security;

import com.toolshare.identity.authorization.Permission;
import com.toolshare.identity.authorization.AuthorizationService;
import com.toolshare.identity.domain.IdentityAccount;
import com.toolshare.identity.infrastructure.persistence.IdentityAccountRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/test")
public class TestAuthorizationController {

    private final IdentityAccountRepository identityAccountRepository;
    private final AuthorizationService authorizationService;

    public TestAuthorizationController(IdentityAccountRepository identityAccountRepository, AuthorizationService authorizationService) {
        this.identityAccountRepository = identityAccountRepository;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/protected")
    public ResponseEntity<String> protectedEndpoint() {
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/renter-only")
    @PreAuthorize("hasAuthority('RENTER_ACCESS')")
    public ResponseEntity<String> renterOnly() {
        return ResponseEntity.ok("renter");
    }

    @GetMapping("/owner-only")
    @PreAuthorize("hasAuthority('OWNER_ACCESS')")
    public ResponseEntity<String> ownerOnly() {
        return ResponseEntity.ok("owner");
    }

    @GetMapping("/admin-only")
    @PreAuthorize("hasAuthority('ADMIN_ACCESS')")
    public ResponseEntity<String> adminOnly() {
        return ResponseEntity.ok("admin");
    }
}
