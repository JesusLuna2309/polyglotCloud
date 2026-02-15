package com.jesusLuna.polyglotCloud.service;

import java.time.Duration;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.jesusLuna.polyglotCloud.config.RateLimitConfig;
import com.jesusLuna.polyglotCloud.exception.RateLimitExceededException;
import com.jesusLuna.polyglotCloud.models.enums.RateLimitType;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final ProxyManager<byte[]> proxyManager;
    private final RateLimitConfig rateLimitConfig;
    private final AbuseDetectionService abuseDetectionService;

    /**
     * Verifica y consume un token del rate limiter para un usuario
     */
    public void checkRateLimit(RateLimitType limitType, UUID userId, String ipAddress) {
        if (!rateLimitConfig.isRateLimitingEnabled()) {
            log.debug("Rate limiting disabled, skipping check");
            return;
        }

        String identifier = userId != null ? userId.toString() : ipAddress;
        String bucketKey = limitType.generateKey(identifier);
        
        try {
            Bucket bucket = proxyManager.builder()
                    .build(bucketKey.getBytes(), () -> createBucketConfiguration(limitType));

            if (!bucket.tryConsume(1)) {
                // Rate limit excedido
                log.warn("Rate limit exceeded for {} - Type: {}, Identifier: {}", 
                        bucketKey, limitType, identifier);
                
                // Registrar intento de abuso
                abuseDetectionService.recordRateLimitViolation(identifier, limitType, ipAddress);
                
                // Obtener tiempo hasta el próximo refill
                var probe = bucket.estimateAbilityToConsume(1);
                Duration waitTime = Duration.ofNanos(probe.getNanosToWaitForRefill());
                
                throw new RateLimitExceededException(limitType, waitTime);
            }

            log.debug("Rate limit check passed for {} - Type: {}", identifier, limitType);
            
        } catch (RateLimitExceededException e) {
            throw e; // Re-lanzar excepciones de rate limit
        } catch (Exception e) {
            log.error("Error checking rate limit for {}: {}", bucketKey, e.getMessage(), e);
            // En caso de error, permitir la request para no bloquear usuarios legítimos
        }
    }

    /**
     * Verifica múltiples tipos de rate limit para mayor seguridad
     */
    public void checkMultipleRateLimits(UUID userId, String ipAddress) {
        if (userId != null) {
            // Usuario registrado - límites más permisivos
            checkRateLimit(RateLimitType.TRANSLATION_REQUEST_USER, userId, ipAddress);
            checkRateLimit(RateLimitType.TRANSLATION_REQUEST_USER_DAILY, userId, ipAddress);
        } else {
            // Usuario anónimo - límites más estrictos
            checkRateLimit(RateLimitType.TRANSLATION_REQUEST_IP, null, ipAddress);
            checkRateLimit(RateLimitType.TRANSLATION_REQUEST_IP_DAILY, null, ipAddress);
        }
    }

    /**
     * Verifica rate limit para votaciones
     */
    public void checkVoteRateLimit(UUID userId, String ipAddress) {
        checkRateLimit(RateLimitType.VOTE_REQUEST_USER, userId, ipAddress);
    }

    /**
     * Obtiene información del estado actual del rate limit
     */
    public RateLimitInfo getRateLimitInfo(RateLimitType limitType, UUID userId, String ipAddress) {
        if (!rateLimitConfig.isRateLimitingEnabled()) {
            return new RateLimitInfo(limitType.getMaxRequests(), limitType.getMaxRequests(), 
                                   Duration.ZERO, false);
        }

        String identifier = userId != null ? userId.toString() : ipAddress;
        String bucketKey = limitType.generateKey(identifier);
        
        try {
            Bucket bucket = proxyManager.builder()
                    .build(bucketKey.getBytes(), () -> createBucketConfiguration(limitType));

            var snapshot = bucket.getAvailableTokens();
            var probe = bucket.estimateAbilityToConsume(1);
            
            return new RateLimitInfo(
                limitType.getMaxRequests(),
                (int) snapshot,
                Duration.ofNanos(probe.getNanosToWaitForRefill()),
                snapshot == 0
            );
            
        } catch (Exception e) {
            log.error("Error getting rate limit info for {}: {}", bucketKey, e.getMessage());
            return new RateLimitInfo(limitType.getMaxRequests(), limitType.getMaxRequests(), 
                                   Duration.ZERO, false);
        }
    }

    private BucketConfiguration createBucketConfiguration(RateLimitType limitType) {
        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(limitType.getMaxRequests())
                .refillIntervally(limitType.getMaxRequests(), limitType.getDuration())
                .build();

        return BucketConfiguration.builder()
                .addLimit(bandwidth)
                .build();
    }

    /**
     * Record con información del estado del rate limit
     */
    public record RateLimitInfo(
        int maxRequests,
        int remainingRequests,
        Duration resetTime,
        boolean isBlocked
    ) {}
}