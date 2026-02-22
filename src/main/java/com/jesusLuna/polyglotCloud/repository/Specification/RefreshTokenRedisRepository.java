package com.jesusLuna.polyglotCloud.repository.Specification;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import com.jesusLuna.polyglotCloud.models.RefreshToken;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Repository
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenRedisRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String TOKEN_PREFIX = "REFRESH_TOKEN:";
    private static final String USER_TOKENS_PREFIX = "USER_TOKENS:";

    /**
     * Guarda un refresh token en Redis con TTL automático
     */
    public RefreshToken save(RefreshToken token) {
        try {
            String tokenKey = TOKEN_PREFIX + token.getToken();
            String userMappingKey = token.getUserMappingKey();
            
            // Calcular TTL basado en expiración
            long ttlSeconds = Duration.between(Instant.now(), token.getExpiresAt()).getSeconds();
            
            if (ttlSeconds <= 0) {
                log.warn("Attempting to save expired token: {}", token.getId());
                return token;
            }
            
            // Guardar token principal
            redisTemplate.opsForValue().set(tokenKey, token, ttlSeconds, TimeUnit.SECONDS);
            
            // Guardar mapeo usuario -> token para consultas por usuario
            redisTemplate.opsForValue().set(userMappingKey, token.getId().toString(), ttlSeconds, TimeUnit.SECONDS);
            
            log.debug("Saved refresh token to Redis: {} (TTL: {} seconds)", token.getId(), ttlSeconds);
            return token;
            
        } catch (Exception e) {
            log.error("Error saving refresh token to Redis: {}", token.getId(), e);
            throw new RuntimeException("Failed to save refresh token", e);
        }
    }

    /**
     * Busca un refresh token por su valor
     */
    public Optional<RefreshToken> findByToken(String tokenString) {
        try {
            String key = TOKEN_PREFIX + tokenString;
            RefreshToken token = (RefreshToken) redisTemplate.opsForValue().get(key);
            
            if (token != null) {
                log.debug("Found refresh token in Redis: {}", token.getId());
            }
            
            return Optional.ofNullable(token);
            
        } catch (Exception e) {
            log.error("Error finding refresh token in Redis: {}", tokenString, e);
            return Optional.empty();
        }
    }

    /**
     * Obtiene todos los tokens activos de un usuario
     */
    public List<RefreshToken> findActiveByUserId(UUID userId) {
        try {
            String pattern = USER_TOKENS_PREFIX + userId + ":*";
            Set<String> keys = redisTemplate.keys(pattern);
            
            if (keys == null || keys.isEmpty()) {
                return List.of();
            }
            
            // Obtener IDs de tokens del usuario
            List<String> tokenIds = keys.stream()
                    .map(key -> (String) redisTemplate.opsForValue().get(key))
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
            
            // Buscar los tokens completos
            return tokenIds.stream()
                    .map(this::findById)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .filter(token -> !token.isRevoked() && !token.isExpired())
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            log.error("Error finding active tokens for user: {}", userId, e);
            return List.of();
        }
    }

    /**
     * Busca token por ID (usado internamente)
     */
    private Optional<RefreshToken> findById(String tokenId) {
        try {
            // Necesitamos buscar por patrón ya que no tenemos mapeo directo ID -> token string
            Set<String> tokenKeys = redisTemplate.keys(TOKEN_PREFIX + "*");
            
            if (tokenKeys != null) {
                for (String key : tokenKeys) {
                    RefreshToken token = (RefreshToken) redisTemplate.opsForValue().get(key);
                    if (token != null && tokenId.equals(token.getId().toString())) {
                        return Optional.of(token);
                    }
                }
            }
            
            return Optional.empty();
            
        } catch (Exception e) {
            log.error("Error finding token by ID: {}", tokenId, e);
            return Optional.empty();
        }
    }

    /**
     * Revoca un token específico
     */
    public void revokeToken(String tokenString) {
        try {
            String key = TOKEN_PREFIX + tokenString;
            RefreshToken token = (RefreshToken) redisTemplate.opsForValue().get(key);
            
            if (token != null) {
                token.revoke();
                // Guardar token revocado con TTL corto (para auditoría temporal)
                redisTemplate.opsForValue().set(key, token, 300, TimeUnit.SECONDS); // 5 minutos
                
                log.info("Revoked refresh token: {}", token.getId());
            }
            
        } catch (Exception e) {
            log.error("Error revoking token: {}", tokenString, e);
        }
    }

    /**
     * Revoca todos los tokens de un usuario
     */
    public int revokeAllByUserId(UUID userId) {
        try {
            List<RefreshToken> userTokens = findActiveByUserId(userId);
            
            for (RefreshToken token : userTokens) {
                revokeToken(token.getToken());
            }
            
            log.info("Revoked {} tokens for user: {}", userTokens.size(), userId);
            return userTokens.size();
            
        } catch (Exception e) {
            log.error("Error revoking all tokens for user: {}", userId, e);
            return 0;
        }
    }

    /**
     * Elimina un token completamente de Redis
     */
    public void delete(String tokenString) {
        try {
            Optional<RefreshToken> tokenOpt = findByToken(tokenString);
            
            if (tokenOpt.isPresent()) {
                RefreshToken token = tokenOpt.get();
                
                // Eliminar token principal
                redisTemplate.delete(TOKEN_PREFIX + tokenString);
                
                // Eliminar mapeo de usuario
                redisTemplate.delete(token.getUserMappingKey());
                
                log.debug("Deleted refresh token from Redis: {}", token.getId());
            }
            
        } catch (Exception e) {
            log.error("Error deleting token: {}", tokenString, e);
        }
    }

    /**
     * Cuenta tokens activos de un usuario
     */
    public long countActiveTokensByUserId(UUID userId, Instant now) {
        return findActiveByUserId(userId).size();
    }

    /**
     * Verifica si un token es válido (existe y no está revocado/expirado)
     */
    public boolean isTokenValid(String tokenString) {
        return findByToken(tokenString)
                .map(RefreshToken::isValid)
                .orElse(false);
    }

    /**
     * Limpieza manual de tokens expirados (Redis TTL se encarga automáticamente)
     * Este método es para casos especiales o mantenimiento
     */
    public int cleanupExpiredTokens() {
        try {
            Set<String> tokenKeys = redisTemplate.keys(TOKEN_PREFIX + "*");
            int cleanedCount = 0;
            
            if (tokenKeys != null) {
                for (String key : tokenKeys) {
                    RefreshToken token = (RefreshToken) redisTemplate.opsForValue().get(key);
                    if (token != null && token.isExpired()) {
                        redisTemplate.delete(key);
                        cleanedCount++;
                    }
                }
            }
            
            log.info("Manual cleanup removed {} expired tokens", cleanedCount);
            return cleanedCount;
            
        } catch (Exception e) {
            log.error("Error during manual token cleanup", e);
            return 0;
        }
    }
}
