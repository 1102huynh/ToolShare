package com.toolshare.identity;

import com.toolshare.identity.authorization.Permission;
import com.toolshare.identity.authorization.Role;
import com.toolshare.identity.domain.IdentityAccount;
import com.toolshare.identity.infrastructure.persistence.IdentityAccountRepository;
import com.toolshare.identity.infrastructure.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RoleAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private IdentityAccountRepository identityAccountRepository;

    @Test
    void unauthenticatedRequestToProtectedRoleEndpointIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/test/renter-only"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserWithoutRequiredPermissionIsForbidden() throws Exception {
        IdentityAccount account = identityAccountRepository.save(IdentityAccount.register("no-perm@example.com", "hashed-password"));
        account.addRole(Role.RENTER);
        identityAccountRepository.save(account);

        String token = jwtTokenService.generateAccessToken(account.getId(), account.getEmail());

        mockMvc.perform(get("/api/v1/test/owner-only")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedUserWithRequiredPermissionIsAllowed() throws Exception {
        IdentityAccount account = identityAccountRepository.save(IdentityAccount.register("owner@example.com", "hashed-password"));
        account.addRole(Role.OWNER);
        account.markEmailVerified();
        account.activate();
        identityAccountRepository.save(account);

        String token = jwtTokenService.generateAccessToken(account.getId(), account.getEmail());

        mockMvc.perform(get("/api/v1/test/owner-only")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void adminRoleIsAllowedForAdminEndpoint() throws Exception {
        IdentityAccount account = identityAccountRepository.save(IdentityAccount.register("admin@example.com", "hashed-password"));
        account.addRole(Role.ADMIN);
        account.markEmailVerified();
        account.activate();
        identityAccountRepository.save(account);

        String token = jwtTokenService.generateAccessToken(account.getId(), account.getEmail());

        mockMvc.perform(get("/api/v1/test/admin-only")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
