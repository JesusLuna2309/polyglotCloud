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
    
    private static final String TOKEN_PREFIX = "REFRESH_TOKEN:";
    private static final String USER_TOKENS_PREFIX = "USER_TOKENS:";

    /**
     * Guarda un refresh token en Redis con TTL automático
     */
    public RefreshToken save(RefreshToken token) {
        String hashedToken = encoder.encode(token.getToken());
        String tokenKey = TOKEN_PREFIX + hashedToken;
        String userIndexKey = USER_TOKENS_PREFIX + token.getUserId();

        long ttlSeconds = Duration.between(Instant.now(), token.getExpiresAt()).getSeconds();

        redisTemplate.opsForValue().set(tokenKey, token, ttlSeconds, TimeUnit.SECONDS);

        // índice por usuario
        redisTemplate.opsForSet().add(userIndexKey, hashedToken);
        if (Boolean.FALSE.equals(redisTemplate.hasKey(userIndexKey))) {
                redisTemplate.expire(userIndexKey, ttlSeconds, TimeUnit.SECONDS);
        }
        return token;
    }
    /**
     * Busca un refresh token por su valor
     */
    public Optional<RefreshToken> findByToken(String tokenString) {
        try {
            String hashedToken = encoder.encode(tokenString);
            String key = TOKEN_PREFIX + hashedToken;
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
            String indexKey = USER_TOKENS_PREFIX + userId;

            Set<Object> tokenIds = redisTemplate.opsForSet().members(indexKey);
            
            if (tokenIds == null || tokenIds.isEmpty()) {
                return List.of();
            }
            
            List<RefreshToken> tokens = tokenIds.stream()
                    .map(id -> findByToken(id.toString()))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .filter(token -> !token.isRevoked() && !token.isExpired())
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
            String hashedToken = encoder.encode(tokenString);
            String key = TOKEN_PREFIX + hashedToken;
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
            
            if (tokenOpt.isEmpty()) {
                log.warn("Attempted to delete non-existent token: {}", tokenString);
                return;
            }

            RefreshToken token = tokenOpt.get();

            redisTemplate.delete(TOKEN_PREFIX + tokenString);

            redisTemplate.opsForSet().remove(USER_TOKENS_PREFIX + token.getUserId(), tokenString);

            log.info("Deleted refresh token: {}", token.getId());
            
        } catch (Exception e) {
            log.error("Error deleting token: {}", tokenString, e);
        }
    }

    /**
     * Cuenta tokens activos de un usuario
     */
    public long countActiveTokensByUserId(UUID userId, Instant now) {
        return redisTemplate.opsForSet().size(USER_TOKENS_PREFIX + userId);
    }

    /**
     * Verifica si un token es válido (existe y no está revocado/expirado)
     */
    public boolean isTokenValid(String tokenString) {
        return findByToken(tokenString)
                .map(RefreshToken::isValid)
                .orElse(false);
    }
}