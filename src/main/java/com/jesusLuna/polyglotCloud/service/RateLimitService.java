package com.jesusLuna.polyglotCloud.service;

import java.time.Duration;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.jesusLuna.polyglotCloud.config.RateLimitConfig;
import com.jesusLuna.polyglotCloud.dto.RateLimitsDTO.RateLimitStats;
import com.jesusLuna.polyglotCloud.exception.RateLimitExceededException;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final ProxyManager<String> proxyManager;
    private final RateLimitConfig rateLimitConfig;
    private final AbuseDetectionService abuseDetectionService;

    /**
     * Verifica rate limiting para usuarios autenticados
     */
    public void checkUserRateLimit(UUID userId, String endpoint) {
        String bucketKey = "rate_limit:user:" + userId + ":" + endpoint;
        
        Bucket bucket = proxyManager.builder()
                .build(bucketKey, getUserBucketConfiguration());
        
        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit exceeded for user {} on endpoint {}", userId, endpoint);
            abuseDetectionService.recordAbuse(userId.toString(), "USER_RATE_LIMIT", endpoint);
            throw new RateLimitExceededException(
                "Too many requests. Please wait before making another translation request."
            );
        }
        
        log.debug("Rate limit check passed for user {} on endpoint {}", userId, endpoint);
    }

    /**
     * Verifica rate limiting para direcciones IP (usuarios anónimos o adicional)
     */
    public void checkIpRateLimit(String ipAddress, String endpoint) {
        String bucketKey = "rate_limit:ip:" + ipAddress + ":" + endpoint;
        
        Bucket bucket = proxyManager.builder()
                .build(bucketKey, getIpBucketConfiguration());
        
        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit exceeded for IP {} on endpoint {}", ipAddress, endpoint);
            abuseDetectionService.recordAbuse(ipAddress, "IP_RATE_LIMIT", endpoint);
            throw new RateLimitExceededException(
                "Too many requests from this IP address. Please wait before trying again."
            );
        }
        
        log.debug("Rate limit check passed for IP {} on endpoint {}", ipAddress, endpoint);
    }

    /**
     * Verifica si un usuario está temporalmente bloqueado por abuso
     */
    public void checkUserBlocked(UUID userId) {
        if (abuseDetectionService.isUserBlocked(userId)) {
            log.warn("Blocked user {} attempted to make request", userId);
            throw new RateLimitExceededException(
                "Your account has been temporarily restricted due to suspicious activity. Please contact support."
            );
        }
    }

    /**
     * Obtiene stats de rate limiting para un usuario
     */
    public RateLimitStats getUserRateLimitStats(UUID userId, String endpoint) {
        String bucketKey = "rate_limit:user:" + userId + ":" + endpoint;
        
        Bucket bucket = proxyManager.builder()
                .build(bucketKey, getUserBucketConfiguration());
        
        long consumption = bucket.getAvailableTokens();
        long capacity = rateLimitConfig.getTranslationRequestsPerMinute(); // Usar directamente el config

        
        return new RateLimitStats(
            capacity,
            consumption,
            capacity - consumption,
            bucket.getAvailableTokens() == 0
        );
    }

    private BucketConfiguration getUserBucketConfiguration() {
        return BucketConfiguration.builder()
                .addLimit(limit -> limit
                    .capacity(rateLimitConfig.getTranslationRequestsPerMinute())
                    .refillGreedy(rateLimitConfig.getTranslationRequestsPerMinute(), Duration.ofMinutes(1))
                )
                .addLimit(limit -> limit
                    .capacity(rateLimitConfig.getTranslationRequestsPerHour())
                    .refillGreedy(rateLimitConfig.getTranslationRequestsPerHour(), Duration.ofHours(1))
                )
                .build();
    }

    private BucketConfiguration getIpBucketConfiguration() {
        return BucketConfiguration.builder()
                .addLimit(limit -> limit
                    .capacity(rateLimitConfig.getAnonymousRequestsPerMinute())
                    .refillGreedy(rateLimitConfig.getAnonymousRequestsPerMinute(), Duration.ofMinutes(1))
                )
                .build();
    }
}