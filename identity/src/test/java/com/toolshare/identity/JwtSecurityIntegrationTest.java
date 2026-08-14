package com.toolshare.identity;

import com.toolshare.identity.infrastructure.security.JwtTokenService;
import com.toolshare.identity.domain.IdentityAccount;
import com.toolshare.identity.infrastructure.persistence.IdentityAccountRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class JwtSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private IdentityAccountRepository identityAccountRepository;

    @Value("${toolshare.identity.jwt.secret}")
    private String jwtSecret;

    @Test
    void unauthenticatedRequestToProtectedEndpointIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/test/protected"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedJwtIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/test/protected").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validJwtIsAuthenticated() throws Exception {
        IdentityAccount account = identityAccountRepository.save(IdentityAccount.register("user@example.com", "hashed-password"));
        account.markEmailVerified();
        account.activate();
        identityAccountRepository.save(account);

        String token = jwtTokenService.generateAccessToken(account.getId(), account.getEmail());

        mockMvc.perform(get("/api/v1/test/protected")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void expiredJwtIsRejected() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        String expiredToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("email", "user@example.com")
                .issuedAt(Date.from(Instant.now().minusSeconds(3600)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(key)
                .compact();

        mockMvc.perform(get("/api/v1/test/protected")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }
}
