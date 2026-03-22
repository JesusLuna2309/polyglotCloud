package com.jesusLuna.polyglotCloud.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.jesusLuna.polyglotCloud.dto.RateLimitsDTO.AbuseStats;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AbuseDetectionService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String ABUSE_KEY_PREFIX = "abuse:";
    private static final String BLOCK_KEY_PREFIX = "blocked:";
    
    private static final int ABUSE_THRESHOLD = 5; // 5 intentos de abuso = bloqueo temporal
    private static final Duration ABUSE_WINDOW = Duration.ofHours(1); // Ventana de detección
    private static final Duration BLOCK_DURATION = Duration.ofHours(2); // Tiempo de bloqueo

    /**
     * Registra un intento de abuso
     */
    public void recordAbuse(String identifier, String abuseType, String endpoint) {
        String abuseKey = ABUSE_KEY_PREFIX + abuseType + ":" + identifier;
        
        // Incrementar contador de abuso
        Long abuseCount = redisTemplate.opsForValue().increment(abuseKey);
        
        if (abuseCount == 1) {
            // Primera infracción, establecer TTL
            redisTemplate.expire(abuseKey, ABUSE_WINDOW.getSeconds(), TimeUnit.SECONDS);
        }
        
        log.warn("Abuse recorded: type={}, identifier={}, endpoint={}, count={}", 
                abuseType, identifier, endpoint, abuseCount);
        
        // Si supera el threshold, bloquear temporalmente
        if (abuseCount >= ABUSE_THRESHOLD) {
            blockTemporarily(identifier, abuseType);
        }
        
        // Log detallado para monitoreo
        logAbuseAttempt(identifier, abuseType, endpoint, abuseCount);
    }

    /**
     * Verifica si un usuario está bloqueado
     */
    public boolean isUserBlocked(UUID userId) {
        String blockKey = BLOCK_KEY_PREFIX + "user:" + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(blockKey));
    }

    /**
     * Verifica si una IP está bloqueada
     */
    public boolean isIpBlocked(String ipAddress) {
        String blockKey = BLOCK_KEY_PREFIX + "ip:" + ipAddress;
        return Boolean.TRUE.equals(redisTemplate.hasKey(blockKey));
    }

    /**
     * Bloquea temporalmente un identificador
     */
    private void blockTemporarily(String identifier, String abuseType) {
        String blockKey = BLOCK_KEY_PREFIX + abuseType.toLowerCase() + ":" + identifier;
        
        redisTemplate.opsForValue().set(blockKey, Instant.now().toString(), 
                BLOCK_DURATION.getSeconds(), TimeUnit.SECONDS);
        
        log.error("Temporary block applied: identifier={}, type={}, duration={}", 
                identifier, abuseType, BLOCK_DURATION);
        
        // Notificar a administradores (opcional)
        notifyAdmins(identifier, abuseType);
    }

    /**
     * Obtiene estadísticas de abuso para un identificador
     */
    public AbuseStats getAbuseStats(String identifier, String abuseType) {
        String abuseKey = ABUSE_KEY_PREFIX + abuseType + ":" + identifier;
        String blockKey = BLOCK_KEY_PREFIX + abuseType.toLowerCase() + ":" + identifier;
        
        Integer abuseCount = (Integer) redisTemplate.opsForValue().get(abuseKey);
        boolean isBlocked = Boolean.TRUE.equals(redisTemplate.hasKey(blockKey));
        Long blockTtl = isBlocked ? redisTemplate.getExpire(blockKey, TimeUnit.SECONDS) : null;
        
        return new AbuseStats(
            identifier,
            abuseType,
            abuseCount != null ? abuseCount : 0,
            isBlocked,
            blockTtl
        );
    }

    private void logAbuseAttempt(String identifier, String abuseType, String endpoint, Long count) {
        // Log estructurado para sistemas de monitoreo (ELK, Splunk, etc.)
        log.error("SECURITY_ALERT: abuse_type={}, identifier={}, endpoint={}, count={}, timestamp={}", 
                abuseType, identifier, endpoint, count, Instant.now());
    }

    private void notifyAdmins(String identifier, String abuseType) {
        // TODO: Implementar notificación a admins (email, Slack, etc.)
        log.error("ADMIN_ALERT: Automatic block applied to {} (type: {})", identifier, abuseType);
    }
}