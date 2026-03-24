package com.jesusLuna.polyglotCloud.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

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
