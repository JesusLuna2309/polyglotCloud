package com.jesusLuna.polyglotCloud.service;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.jesusLuna.polyglotCloud.models.enums.RateLimitType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AbuseDetectionService {

    private final CacheService cacheService;

    @Value("${app.abuse-detection.violation-threshold:5}")
    private int violationThreshold;

    @Value("${app.abuse-detection.tracking-window-hours:24}")
    private int trackingWindowHours;

    /**
     * Registra una violación de rate limit
     */
    public void recordRateLimitViolation(String identifier, RateLimitType limitType, String ipAddress) {
        String violationKey = generateViolationKey(identifier);
        
        try {
            // Incrementar contador de violaciones
            String currentCount = (String) cacheService.get(violationKey);
            int violations = currentCount != null ? Integer.parseInt(currentCount) : 0;
            violations++;
            
            // Guardar con TTL de 24 horas
            cacheService.save(violationKey, String.valueOf(violations),
                            trackingWindowHours, TimeUnit.HOURS);
            
            log.warn("Abuse violation recorded - Identifier: {}, Type: {}, IP: {}, Total violations: {}",
                    identifier, limitType, ipAddress, violations);
            
            // Verificar si necesitamos escalation
            checkForEscalation(identifier, violations, ipAddress, limitType);
            
        } catch (Exception e) {
            log.error("Error recording abuse violation for {}: {}", identifier, e.getMessage());
        }
    }

    /**
     * Verifica si un identificador está marcado como abusivo
     */
    public boolean isMarkedAsAbusive(String identifier) {
        try {
            String violationKey = generateViolationKey(identifier);
            String countStr = (String) cacheService.get(violationKey);
            
            if (countStr != null) {
                int violations = Integer.parseInt(countStr);
                return violations >= violationThreshold;
            }
            
            return false;
        } catch (Exception e) {
            log.error("Error checking abuse status for {}: {}", identifier, e.getMessage());
            return false; // En caso de error, no bloquear
        }
    }

    /**
     * Obtiene el número de violaciones para un identificador
     */
    public int getViolationCount(String identifier) {
        try {
            String violationKey = generateViolationKey(identifier);
            String countStr = (String) cacheService.get(violationKey);
            return countStr != null ? Integer.parseInt(countStr) : 0;
        } catch (Exception e) {
            log.error("Error getting violation count for {}: {}", identifier, e.getMessage());
            return 0;
        }
    }

    /**
     * Marca temporalmente una IP como sospechosa
     */
    public void markIpAsSuspicious(String ipAddress, Duration blockDuration) {
        String blockKey = generateBlockKey(ipAddress);
        
        try {
            cacheService.save(blockKey, Instant.now().toString(), 
                             blockDuration.toSeconds(), TimeUnit.SECONDS);
            
            log.warn("IP {} marked as suspicious for {} seconds", ipAddress, blockDuration.getSeconds());
        } catch (Exception e) {
            log.error("Error marking IP as suspicious {}: {}", ipAddress, e.getMessage());
        }
    }

    /**
     * Verifica si una IP está bloqueada temporalmente
     */
    public boolean isIpBlocked(String ipAddress) {
        try {
            String blockKey = generateBlockKey(ipAddress);
            return cacheService.get(blockKey) != null;
        } catch (Exception e) {
            log.error("Error checking IP block status for {}: {}", ipAddress, e.getMessage());
            return false; // En caso de error, permitir acceso
        }
    }

    /**
     * Limpia las violaciones para un identificador (para moderadores)
     */
    public void clearViolations(String identifier) {
        try {
            String violationKey = generateViolationKey(identifier);
            cacheService.delete(violationKey);
            log.info("Violations cleared for identifier: {}", identifier);
        } catch (Exception e) {
            log.error("Error clearing violations for {}: {}", identifier, e.getMessage());
        }
    }

    private void checkForEscalation(String identifier, int violations, String ipAddress, RateLimitType limitType) {
        if (violations >= violationThreshold) {
            log.error("SECURITY ALERT: Excessive rate limit violations detected - " +
                     "Identifier: {}, IP: {}, Violations: {}, Type: {}", 
                     identifier, ipAddress, violations, limitType);
            
            // Bloquear temporalmente la IP
            markIpAsSuspicious(ipAddress, Duration.ofHours(1));
            
            // Aquí puedes agregar notificaciones adicionales:
            // - Email a administradores
            // - Webhook a sistema de monitoreo
            // - Log estructurado para SIEM
        }
    }

    private String generateViolationKey(String identifier) {
        return "abuse:violations:" + identifier;
    }

    private String generateBlockKey(String ipAddress) {
        return "abuse:blocked_ip:" + ipAddress;
    }
}