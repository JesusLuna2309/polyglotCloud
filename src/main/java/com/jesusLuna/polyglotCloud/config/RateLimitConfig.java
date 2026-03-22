package com.jesusLuna.polyglotCloud.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class RateLimitConfig {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.rate-limit.translation.requests-per-minute:10}")
    private int translationRequestsPerMinute;

    @Value("${app.rate-limit.translation.requests-per-hour:50}")
    private int translationRequestsPerHour;

    @Value("${app.rate-limit.anonymous.requests-per-minute:2}")
    private int anonymousRequestsPerMinute;

    @Bean
    public ProxyManager<byte[]> proxyManager() {

        RedisClient redisClient = RedisClient.create("redis://localhost:6379");

        return LettuceBasedProxyManager
                .builderFor(redisClient)
                .build();
    }

    public int getTranslationRequestsPerMinute() {
        return translationRequestsPerMinute;
    }

    public int getTranslationRequestsPerHour() {
        return translationRequestsPerHour;
    }

    public int getAnonymousRequestsPerMinute() {
        return anonymousRequestsPerMinute;
    }
}
