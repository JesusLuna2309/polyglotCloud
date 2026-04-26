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
     * Verifica rate limiting general para usuarios autenticados
     */
    public void checkGeneralUserRateLimit(UUID userId) {
        String bucketKey = "rate_limit:user:" + userId + ":general";
        
        Bucket bucket = proxyManager.builder()
                .build(bucketKey, getGeneralUserBucketConfiguration());
        
        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit exceeded for user {}", userId);
            abuseDetectionService.recordAbuse(userId.toString(), "USER_RATE_LIMIT", "general");
            throw new RateLimitExceededException(
                "Too many requests. Please wait before making another translation request."
            );
        }
        log.debug("Rate limit check passed for user {}", userId);
    }

    /**
     * Verifica rate limiting para traducciones (más restrictivo)
     */
    public void checkTranslationRateLimit(UUID userId) {
        String bucketKey = "rate_limit:user:" + userId + ":translations";
        
        Bucket bucket = proxyManager.builder()
                .build(bucketKey, getTranslationBucketConfiguration());
        
        if (!bucket.tryConsume(1)) {
            log.warn("Translation rate limit exceeded for user {}", userId);
            abuseDetectionService.recordAbuse(userId.toString(), "USER_TRANSLATION_RATE_LIMIT", "translations");
            throw new RateLimitExceededException(
                "Translation rate limit exceeded. Please wait before making another translation request."
            );
        }
        
        log.debug("Translation rate limit check passed for user {}", userId);
    }

    /**
     * Verifica rate limiting para autenticación (más restrictivo)
     */
    public void checkAuthRateLimit(String ipAddress) {
        String bucketKey = "rate_limit:auth:ip:" + ipAddress;
        
        Bucket bucket = proxyManager.builder()
                .build(bucketKey, getAuthBucketConfiguration());
        
        if (!bucket.tryConsume(1)) {
            log.warn("Auth rate limit exceeded for IP {}", ipAddress);
            abuseDetectionService.recordAbuse(ipAddress, "IP_AUTH_RATE_LIMIT", "auth");
            throw new RateLimitExceededException(
                "Too many authentication attempts. Please wait before trying again."
            );
        }
        
        log.debug("Auth rate limit check passed for IP {}", ipAddress);
    }

    /**
     * Verifica rate limiting para direcciones IP (usuarios anónimos)
     */
    public void checkIpRateLimit(String ipAddress, String endpoint) {
        String bucketKey = "rate_limit:ip:" + ipAddress + ":" + endpoint;
        
        Bucket bucket = proxyManager.builder()
                .build(bucketKey, getIpBucketConfiguration());
        
        if (!bucket.tryConsume(1)) {
            log.warn("IP rate limit exceeded for IP {} on endpoint {}", ipAddress, endpoint);
            abuseDetectionService.recordAbuse(ipAddress, "IP_RATE_LIMIT", endpoint);
            throw new RateLimitExceededException(
                "Too many requests from this IP address. Please wait before trying again."
            );
        }
        
        log.debug("IP rate limit check passed for IP {} on endpoint {}", ipAddress, endpoint);
    }


    /**
     * Método unificado para chequear por endpoint
     */
    public void checkUserRateLimit(UUID userId, String endpoint) {
        switch (endpoint) {
            case "translations":
                checkTranslationRateLimit(userId);
                break;
            case "auth":
                // Para auth usamos IP, no user (porque puede que no esté autenticado aún)
                break;
            default:
                checkGeneralUserRateLimit(userId);
                break;
        }
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
        BucketConfiguration config = getConfigurationForEndpoint(endpoint);
        
        Bucket bucket = proxyManager.builder().build(bucketKey, config);
        
        long capacity = getCapacityForEndpoint(endpoint);
        long available = bucket.getAvailableTokens();
        
        return new RateLimitStats(
            capacity,
            available,
            capacity - available,
            available == 0
        );
    }

    // ======================================
    // CONFIGURACIONES DE BUCKETS
    // ======================================

    /**
     * Configuración general para usuarios (50/minuto)
     */
    private BucketConfiguration getGeneralUserBucketConfiguration() {
        return BucketConfiguration.builder()
                .addLimit(limit -> limit
                    .capacity(rateLimitConfig.getGlobalRequestsPerMinute())
                    .refillGreedy(rateLimitConfig.getGlobalRequestsPerMinute(), Duration.ofMinutes(1))
                )
                .build();
    }

    /**
     * Configuración para traducciones (5/minuto, 20/hora)
     */
    private BucketConfiguration getTranslationBucketConfiguration() {
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

    /**
     * Configuración para autenticación (3/minuto, 10/hora)
     */
    private BucketConfiguration getAuthBucketConfiguration() {
        return BucketConfiguration.builder()
                .addLimit(limit -> limit
                    .capacity(rateLimitConfig.getAuthRequestsPerMinute())
                    .refillGreedy(rateLimitConfig.getAuthRequestsPerMinute(), Duration.ofMinutes(1))
                )
                .addLimit(limit -> limit
                    .capacity(rateLimitConfig.getAuthRequestsPerHour())
                    .refillGreedy(rateLimitConfig.getAuthRequestsPerHour(), Duration.ofHours(1))
                )
                .build();
    }

    /**
     * Configuración para IPs anónimas
     */
    private BucketConfiguration getIpBucketConfiguration() {
        return BucketConfiguration.builder()
                .addLimit(limit -> limit
                    .capacity(rateLimitConfig.getAnonymousRequestsPerMinute())
                    .refillGreedy(rateLimitConfig.getAnonymousRequestsPerMinute(), Duration.ofMinutes(1))
                )
                .build();
    }

    // Métodos auxiliares
    private BucketConfiguration getConfigurationForEndpoint(String endpoint) {
        return switch (endpoint) {
            case "translations" -> getTranslationBucketConfiguration();
            case "auth" -> getAuthBucketConfiguration();
            default -> getGeneralUserBucketConfiguration();
        };
    }

    private long getCapacityForEndpoint(String endpoint) {
        return switch (endpoint) {
            case "translations" -> rateLimitConfig.getTranslationRequestsPerMinute();
            case "auth" -> rateLimitConfig.getAuthRequestsPerMinute();
            default -> rateLimitConfig.getGlobalRequestsPerMinute();
        };
    }
}