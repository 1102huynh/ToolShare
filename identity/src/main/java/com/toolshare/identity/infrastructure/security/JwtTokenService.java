package com.toolshare.identity.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtTokenService {

    private final SecretKey signingKey;
    private final Duration accessTokenTtl;

    public JwtTokenService(@Value("${toolshare.identity.jwt.secret:change-me-please-change-me-please}") String secret,
                           @Value("${toolshare.identity.jwt.access-token-ttl:PT15M}") Duration accessTokenTtl) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenTtl = accessTokenTtl;
    }

    public String generateAccessToken(UUID accountId, String email) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenTtl.toMillis());

        return Jwts.builder()
                .subject(accountId.toString())
                .claim("email", email)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public Claims validateAndParse(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("JWT is required");
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (claims.getSubject() == null || claims.getSubject().isBlank()) {
                throw new IllegalArgumentException("JWT subject is missing");
            }

            if (claims.get("email") == null || claims.get("email", String.class) == null || claims.get("email", String.class).isBlank()) {
                throw new IllegalArgumentException("JWT email claim is missing");
            }

            if (claims.getExpiration() == null || claims.getExpiration().before(new Date())) {
                throw new ExpiredJwtException(null, claims, "JWT expired");
            }

            return claims;
        } catch (ExpiredJwtException ex) {
            throw ex;
        } catch (JwtException | IllegalArgumentException ex) {
            throw new JwtException("Invalid JWT", ex) {
            };
        }
    }

    public Claims parse(String token) {
        return validateAndParse(token);
    }
}
