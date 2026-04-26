package com.jesusLuna.polyglotCloud.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class RateLimitConfig {

// 🌐 RATE LIMITING GLOBAL
    @Value("${app.rate-limit.global.requests-per-minute:50}")
    private int globalRequestsPerMinute;

    // 🔄 RATE LIMITING TRADUCCIONES
    @Value("${app.rate-limit.translation.requests-per-minute:5}")
    private int translationRequestsPerMinute;

    @Value("${app.rate-limit.translation.requests-per-hour:20}")
    private int translationRequestsPerHour;

    // 🔐 RATE LIMITING AUTENTICACIÓN
    @Value("${app.rate-limit.auth.requests-per-minute:3}")
    private int authRequestsPerMinute;
    
    @Value("${app.rate-limit.auth.requests-per-hour:10}")
    private int authRequestsPerHour;

    // 👤 RATE LIMITING ANÓNIMO
    @Value("${app.rate-limit.anonymous.requests-per-minute:2}")
    private int anonymousRequestsPerMinute;

    // Getters
    public int getGlobalRequestsPerMinute() {
        return globalRequestsPerMinute;
    }

    public int getTranslationRequestsPerMinute() {
        return translationRequestsPerMinute;
    }

    public int getTranslationRequestsPerHour() {
        return translationRequestsPerHour;
    }

    public int getAuthRequestsPerMinute() {
        return authRequestsPerMinute;
    }

    public int getAuthRequestsPerHour() {
        return authRequestsPerHour;
    }

    public int getAnonymousRequestsPerMinute() {
        return anonymousRequestsPerMinute;
    }
}
