package com.jesusLuna.polyglotCloud.models.enums;

import java.time.Duration;

public enum RateLimitType {
    /**
     * Límite para requests de traducción por usuario registrado
     */
    TRANSLATION_REQUEST_USER(
        5,           // 5 requests
        Duration.ofMinutes(1),  // por minuto
        "Translation requests per user"
    ),
    
    /**
     * Límite para requests de traducción por IP (usuarios anónimos)
     */
    TRANSLATION_REQUEST_IP(
        2,           // 2 requests
        Duration.ofMinutes(1),  // por minuto
        "Translation requests per IP"
    ),
    
    /**
     * Límite diario por usuario registrado
     */
    TRANSLATION_REQUEST_USER_DAILY(
        50,          // 50 requests
        Duration.ofDays(1),     // por día
        "Daily translation requests per user"
    ),
    
    /**
     * Límite diario por IP
     */
    TRANSLATION_REQUEST_IP_DAILY(
        10,          // 10 requests
        Duration.ofDays(1),     // por día
        "Daily translation requests per IP"
    ),
    
    /**
     * Límite para votos por usuario
     */
    VOTE_REQUEST_USER(
        20,          // 20 votes
        Duration.ofMinutes(5),  // por 5 minutos
        "Vote requests per user"
    );

    private final int maxRequests;
    private final Duration duration;
    private final String description;

    RateLimitType(int maxRequests, Duration duration, String description) {
        this.maxRequests = maxRequests;
        this.duration = duration;
        this.description = description;
    }

    public int getMaxRequests() {
        return maxRequests;
    }

    public Duration getDuration() {
        return duration;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Genera una clave única para el bucket de rate limiting
     */
    public String generateKey(String identifier) {
        return String.format("rate_limit:%s:%s", this.name().toLowerCase(), identifier);
    }
}
