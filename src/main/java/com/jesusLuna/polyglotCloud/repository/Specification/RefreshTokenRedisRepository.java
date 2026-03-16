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
import com.jesusLuna.polyglotCloud.security.PostQuantumPasswordEncoder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Repository
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenRedisRepository {
    
    
    private final PostQuantumPasswordEncoder encoder; // inyecta tu encoder

    private final RedisTemplate<String, Object> redisTemplate;
    private final TokenSecurityService tokenSecurityService;
    
    private static final String TOKEN_PREFIX = "REFRESH_TOKEN:";
    private static final String USER_TOKENS_PREFIX = "USER_TOKENS:";


    public RefreshToken save(RefreshToken token) {
    // Generar un token seguro (aleatorio) para el usuario
    String rawToken = tokenSecurityService.generateSecureToken("refresh");
    
    // Derivar la clave determinista para Redis usando HMAC
    String redisKey = TOKEN_PREFIX + tokenSecurityService.deriveRedisKey(rawToken);

    long ttlSeconds = Duration.between(Instant.now(), token.getExpiresAt()).getSeconds();

    // Antes de guardar en Redis, asegurarse de no serializar el valor del token en texto plano
    token.setToken(null);

    // Guardar el refresh token en Redis con TTL (sin el valor del token en texto plano)
    redisTemplate.opsForValue().set(redisKey, token, ttlSeconds, TimeUnit.SECONDS);

    // Guardar índice por usuario (puede seguir usando rawToken)
    String userIndexKey = USER_TOKENS_PREFIX + token.getUserId();
    Boolean userIndexKeyExisted = redisTemplate.hasKey(userIndexKey);
    redisTemplate.opsForSet().add(userIndexKey, redisKey);
    
    // Establecer TTL para el índice si no existía
    if (Boolean.FALSE.equals(userIndexKeyExisted)) {
        redisTemplate.expire(userIndexKey, ttlSeconds, TimeUnit.SECONDS);
    }

    // Retornar el token con el valor aleatorio que el cliente usará
    token.setToken(rawToken); // actualizar el token para que se devuelva al cliente
    return token;
}

    /**
     * Busca un refresh token por su valor
     */
    public RefreshToken findByToken(String tokenString) {
    try {
        // Derivar la clave determinista para Redis usando HMAC
        String redisKey = TOKEN_PREFIX + tokenSecurityService.deriveRedisKey(tokenString);

        RefreshToken token = (RefreshToken) redisTemplate.opsForValue().get(redisKey);

        if (token != null) {
            log.debug("Found refresh token in Redis: {}", token.getId());
        }

        return token;

    } catch (Exception e) {
        log.error("Error finding refresh token in Redis: {}", tokenString, e);
        return null;
    }
}

    /**
     * Obtiene todos los tokens activos de un usuario
     */
    public List<RefreshToken> findActiveByUserId(UUID userId) {
        try {
            String indexKey = USER_TOKENS_PREFIX + userId;

            Set<Object> redisKeys  = redisTemplate.opsForSet().members(indexKey);
            
            if (redisKeys == null || redisKeys.isEmpty()) {
                return List.of();
            }
            
            List<RefreshToken> tokens = redisKeys .stream()
                .map(id -> (RefreshToken) redisTemplate.opsForValue().get(id.toString()))
                .filter(token -> token != null && !token.isRevoked() && !token.isExpired())
                .collect(Collectors.toList());

            log.debug("Found {} active refresh tokens for user: {}", tokens.size(), userId);
            return tokens;
                    
        } catch (Exception e) {
            log.error("Error finding active tokens for user: {}", userId, e);
            return List.of();
        }
    }

    /**
     * Revoca un token específico
     */
    public void revokeToken(String tokenString) {
        try {
            // Derivar la clave determinista para Redis usando HMAC
            String redisKey = TOKEN_PREFIX + tokenSecurityService.deriveRedisKey(tokenString);
            RefreshToken token = (RefreshToken) redisTemplate.opsForValue().get(redisKey);
            
            if (token != null) {
                token.revoke();
                // Guardar token revocado con TTL corto (para auditoría temporal)
                redisTemplate.opsForValue().set(redisKey, token, 300, TimeUnit.SECONDS); // 5 minutos
                
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
            String indexKey = USER_TOKENS_PREFIX + userId;
            
            Set<Object> redisKeys = redisTemplate.opsForSet().members(indexKey);
            
            if (redisKeys == null || redisKeys.isEmpty()) {
                return 0;
            }

            int count = 0;
            for (Object key : redisKeys) {
                RefreshToken token = (RefreshToken) redisTemplate.opsForValue().get(key.toString());
                if (token != null) {
                    token.revoke();
                    redisTemplate.opsForValue().set(key.toString(), token, 300, TimeUnit.SECONDS);
                    count++;
                }
            }

            log.info("Revoked {} tokens for user: {}", count, userId);
            return count;
            
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
            String redisKey = TOKEN_PREFIX + tokenSecurityService.deriveRedisKey(tokenString);
            RefreshToken token = (RefreshToken) redisTemplate.opsForValue().get(redisKey);

            if (token == null) {
                log.warn("Attempted to delete non-existent token: {}", tokenString);
                return;
            }

            redisTemplate.delete(redisKey);

            String userIndexKey = USER_TOKENS_PREFIX + token.getUserId();
            redisTemplate.opsForSet().remove(userIndexKey, redisKey);

            log.info("Deleted refresh token: {}", token.getId());

        } catch (Exception e) {
            log.error("Error deleting token: {}", tokenString, e);
        }
    }

    /**
     * Cuenta tokens activos de un usuario
     */
    public long countActiveTokensByUserId(UUID userId, Instant now) {
        String indexKey = USER_TOKENS_PREFIX + userId;
        Set<Object> redisKeys = redisTemplate.opsForSet().members(indexKey);

        if (redisKeys == null || redisKeys.isEmpty()) {
            return 0L;
        }

        long activeCount = 0L;

        for (Object keyObj : redisKeys) {
            if (keyObj == null) {
                continue;
            }

            String redisKey = keyObj.toString();
            RefreshToken token = (RefreshToken) redisTemplate.opsForValue().get(redisKey);

            // Limpia referencias obsoletas en el índice
            if (token == null) {
                redisTemplate.opsForSet().remove(indexKey, redisKey);
                continue;
            }

            if (token.isValid()) {
                activeCount++;
            }
        }

        return activeCount;
    }

    /**
     * Verifica si un token es válido (existe y no está revocado/expirado)
     */
    public boolean isTokenValid(String tokenString) {
         RefreshToken token = findByToken(tokenString);
        return token != null && token.isValid();
    }
}