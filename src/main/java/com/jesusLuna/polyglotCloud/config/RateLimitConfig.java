package com.jesusLuna.polyglotCloud.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class RateLimitConfig {

    @Value("${app.rate-limiting.enabled:true}")
    private boolean rateLimitingEnabled;

    @Bean
    public ProxyManager<byte[]> proxyManager(RedisConnectionFactory connectionFactory) {
        // Obtener el RedisClient de Lettuce desde el ConnectionFactory
        if (connectionFactory instanceof LettuceConnectionFactory lettuceFactory) {
            try {
                // Obtener la configuración de Redis
                String host = lettuceFactory.getHostName();
                int port = lettuceFactory.getPort();
                
                // Crear RedisClient directamente
                RedisClient redisClient = RedisClient.create(String.format("redis://%s:%d", host, port));
                
                return LettuceBasedProxyManager.builderFor(redisClient)
                        .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofDays(1)))
                        .build();
            } catch (Exception e) {
                log.error("Error creating LettuceBasedProxyManager: {}", e.getMessage());
                throw new RuntimeException("Failed to create LettuceBasedProxyManager", e);
            }
        } else {
            throw new IllegalArgumentException("RedisConnectionFactory must be a LettuceConnectionFactory");
        }
    }

    public boolean isRateLimitingEnabled() {
        return rateLimitingEnabled;
    }
}