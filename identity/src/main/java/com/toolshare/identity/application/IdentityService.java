package com.toolshare.identity.application;

import com.toolshare.common.exception.ConflictException;
import com.toolshare.common.exception.ResourceNotFoundException;
import com.toolshare.common.exception.UnauthorizedException;
import com.toolshare.identity.api.dto.AuthResponse;
import com.toolshare.identity.domain.AccountState;
import com.toolshare.identity.domain.IdentityAccount;
import com.toolshare.identity.domain.RefreshToken;
import com.toolshare.identity.domain.VerificationChallenge;
import com.toolshare.identity.infrastructure.email.EmailVerificationSender;
import com.toolshare.identity.infrastructure.persistence.IdentityAccountRepository;
import com.toolshare.identity.infrastructure.persistence.RefreshTokenRepository;
import com.toolshare.identity.infrastructure.persistence.VerificationChallengeRepository;
import com.toolshare.identity.infrastructure.security.JwtTokenService;
import com.toolshare.identity.infrastructure.security.PasswordHashingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class IdentityService {

    private final IdentityAccountRepository identityAccountRepository;
    private final VerificationChallengeRepository verificationChallengeRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordHashingService passwordHashingService;
    private final JwtTokenService jwtTokenService;
    private final EmailVerificationSender emailVerificationSender;

    private static final SecureRandom RANDOM = new SecureRandom();

    public IdentityService(
            IdentityAccountRepository identityAccountRepository,
            VerificationChallengeRepository verificationChallengeRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordHashingService passwordHashingService,
            JwtTokenService jwtTokenService,
            EmailVerificationSender emailVerificationSender
    ) {
        this.identityAccountRepository = identityAccountRepository;
        this.verificationChallengeRepository = verificationChallengeRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordHashingService = passwordHashingService;
        this.jwtTokenService = jwtTokenService;
        this.emailVerificationSender = emailVerificationSender;
    }

    @Transactional
    public IdentityAccount register(String email, String password) {
        String normalizedEmail = IdentityAccount.normalizeEmail(email);
        if (identityAccountRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("Account with this email already exists");
        }

        String encodedPassword = passwordHashingService.encode(password);
        IdentityAccount account = new IdentityAccount(normalizedEmail, encodedPassword);
        IdentityAccount saved = identityAccountRepository.save(account);

        String rawVerificationToken = generateSecureToken();
        String tokenHash = hashToken(rawVerificationToken);
        VerificationChallenge challenge = new VerificationChallenge(tokenHash, saved, OffsetDateTime.now().plusMinutes(30));
        verificationChallengeRepository.save(challenge);

        emailVerificationSender.sendVerificationEmail(normalizedEmail);
        return saved;
    }

    @Transactional
    public void verifyEmail(String rawVerificationToken) {
        String tokenHash = hashToken(rawVerificationToken);
        VerificationChallenge challenge = verificationChallengeRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Verification token is invalid"));

        if (challenge.getPurpose() != com.toolshare.identity.domain.VerificationPurpose.EMAIL_VERIFICATION) {
            throw new UnauthorizedException("Verification token is invalid");
        }
        if (challenge.isExpired() || challenge.isUsed()) {
            throw new UnauthorizedException("Verification token is invalid or expired");
        }

        IdentityAccount account = challenge.getIdentityAccount();
        if (account.getState() != com.toolshare.identity.domain.AccountState.PENDING_VERIFICATION) {
            throw new ConflictException("Account is not pending verification");
        }

        account.markEmailVerified();
        account.activate();
        challenge.markUsed();
        identityAccountRepository.save(account);
        verificationChallengeRepository.save(challenge);
    }

    @Transactional
    public AuthResponse login(String email, String password) {
        String normalizedEmail = IdentityAccount.normalizeEmail(email);
        IdentityAccount account = identityAccountRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (!account.canLogin() || !passwordHashingService.matches(password, account.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        String accessToken = jwtTokenService.generateAccessToken(account.getId(), account.getEmail());
        String refreshTokenValue = generateSecureToken();
        String refreshHash = hashToken(refreshTokenValue);
        RefreshToken refreshToken = new RefreshToken(UUID.randomUUID(), account, refreshHash, OffsetDateTime.now().plusDays(7));
        refreshTokenRepository.save(refreshToken);

        account.recordLogin();
        identityAccountRepository.save(account);

        return new AuthResponse(accessToken, refreshTokenValue, "Bearer", 900);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        String tokenHash = hashToken(rawRefreshToken);
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Refresh token is invalid"));

        if (refreshToken.isExpired() || refreshToken.isRevoked() || refreshToken.getReplacedByTokenId() != null) {
            throw new UnauthorizedException("Refresh token is invalid or expired");
        }

        IdentityAccount account = refreshToken.getIdentityAccount();
        if (!account.canLogin()) {
            throw new UnauthorizedException("Account is not eligible to use refresh tokens");
        }

        String nextRefreshTokenValue = generateSecureToken();
        String nextRefreshHash = hashToken(nextRefreshTokenValue);
        RefreshToken nextRefreshToken = new RefreshToken(UUID.randomUUID(), account, nextRefreshHash, OffsetDateTime.now().plusDays(7));

        refreshToken.rotateTo(nextRefreshToken.getTokenId());
        refreshTokenRepository.save(refreshToken);
        refreshTokenRepository.save(nextRefreshToken);

        String accessToken = jwtTokenService.generateAccessToken(account.getId(), account.getEmail());
        return new AuthResponse(accessToken, nextRefreshTokenValue, "Bearer", 900);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String tokenHash = hashToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(token -> {
                    token.revoke();
                    refreshTokenRepository.save(token);
                });
    }

    private String generateSecureToken() {
        byte[] randomBytes = new byte[32];
        RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash token", ex);
        }
    }
}
