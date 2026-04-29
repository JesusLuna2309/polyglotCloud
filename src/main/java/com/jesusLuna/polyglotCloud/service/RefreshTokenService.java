package com.jesusLuna.polyglotCloud.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jesusLuna.polyglotCloud.security.JwtTokenProvider;
import com.jesusLuna.polyglotCloud.exception.BusinessRuleException;
import com.jesusLuna.polyglotCloud.exception.ResourceNotFoundException;
import com.jesusLuna.polyglotCloud.models.RefreshToken;
import com.jesusLuna.polyglotCloud.repository.Specification.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.refresh-token.max-per-user:5}")
    private int maxTokensPerUser;
    
    @Transactional
    public RefreshToken createRefreshToken(UUID userId, String ipAddress, String userAgent) {
        log.info("Creating refresh token for user: {}", userId);

        // Generate JWT refresh token
        String tokenString = jwtTokenProvider.generateRefreshToken(userId);
        Instant expiresAt = Instant.now().plusMillis(jwtTokenProvider.getRefreshExpirationMs());

        // Check if user has too many active tokens
        long activeTokenCount = refreshTokenRepository.countActiveTokensByUserId(userId, Instant.now());
        if (activeTokenCount >= maxTokensPerUser) {
            log.warn("User {} has {} active tokens, revoking oldest ones", userId, activeTokenCount);
            revokeOldestTokens(userId, maxTokensPerUser - 1);
        }

        // Create and save refresh token
        RefreshToken refreshToken = RefreshToken.builder()
                .token(tokenString)
                .userId(userId)
                .expiresAt(expiresAt)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        refreshToken = refreshTokenRepository.save(refreshToken);

        log.info("Refresh token created for user: {}, expires at: {}", userId, expiresAt);
        return refreshToken;
    }

    @Transactional(readOnly = true)
    public RefreshToken validateRefreshToken(String tokenString) {
        log.debug("Validating refresh token");

        RefreshToken refreshToken = refreshTokenRepository.findByToken(tokenString)
                .orElseThrow(() -> new ResourceNotFoundException("Refresh token not found"));

        if (refreshToken.isRevoked()) {
            log.warn("Attempted to use revoked refresh token for user: {}", refreshToken.getUserId());
            throw new BusinessRuleException("Refresh token has been revoked");
        }

        if (refreshToken.isExpired()) {
            log.warn("Attempted to use expired refresh token for user: {}", refreshToken.getUserId());
            throw new BusinessRuleException("Refresh token has expired");
        }

        log.debug("Refresh token validated successfully for user: {}", refreshToken.getUserId());
        return refreshToken;
    }

    @Transactional
    public RefreshToken rotateRefreshToken(String oldTokenString, String ipAddress, String userAgent) {
        log.info("Rotating refresh token");

        // Validate old token
        RefreshToken oldToken = validateRefreshToken(oldTokenString);

        // Create new token
        RefreshToken newToken = createRefreshToken(oldToken.getUserId(), ipAddress, userAgent);

        // Revoke old token
        oldToken.revoke();
        refreshTokenRepository.save(oldToken);

        log.info("Refresh token rotated for user: {}", oldToken.getUserId());
        return newToken;
    }

    @Transactional
    public void revokeRefreshToken(String tokenString) {
        log.info("Revoking refresh token");

        RefreshToken refreshToken = refreshTokenRepository.findByToken(tokenString)
                .orElseThrow(() -> new ResourceNotFoundException("Refresh token not found"));

        refreshToken.revoke();
        refreshTokenRepository.save(refreshToken);

        log.info("Refresh token revoked for user: {}", refreshToken.getUserId());
    }

    @Transactional
    public void revokeAllUserTokens(UUID userId) {
        log.info("Revoking all refresh tokens for user: {}", userId);

        int revokedCount = refreshTokenRepository.revokeAllByUserId(userId);

        log.info("Revoked {} refresh tokens for user: {}", revokedCount, userId);
    }

    @Transactional
    public void revokeOldestTokens(UUID userId, int keepCount) {
        var activeTokens = refreshTokenRepository.findActiveByUserId(userId);
        
        if (activeTokens.size() <= keepCount) {
            return;
        }
    // Sort by creation date and revoke oldest
        activeTokens.stream()
                .sorted((t1, t2) -> t1.getCreatedAt().compareTo(t2.getCreatedAt()))
                .limit(activeTokens.size() - keepCount)
                .forEach(token -> {
                    token.revoke();
                    refreshTokenRepository.save(token);
                });

        log.info("Revoked {} oldest tokens for user: {}", activeTokens.size() - keepCount, userId);
    }

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("Starting cleanup of expired and revoked refresh tokens");

        int expiredCount = refreshTokenRepository.deleteExpiredTokens(Instant.now());
        int revokedCount = refreshTokenRepository.deleteRevokedTokens();

        log.info("Cleanup completed: {} expired tokens, {} revoked tokens deleted", expiredCount, revokedCount);
    }
    
}