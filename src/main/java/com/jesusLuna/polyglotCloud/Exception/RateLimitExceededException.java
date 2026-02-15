package com.jesusLuna.polyglotCloud.exception;

import java.time.Duration;

import com.jesusLuna.polyglotCloud.models.enums.RateLimitType;

public class RateLimitExceededException extends RuntimeException {
    
    private final RateLimitType limitType;
    private final Duration retryAfter;
    
    public RateLimitExceededException(RateLimitType limitType, Duration retryAfter) {
        super(String.format("Rate limit exceeded for %s. Try again in %d seconds", 
                limitType.getDescription(), retryAfter.getSeconds()));
        this.limitType = limitType;
        this.retryAfter = retryAfter;
    }
    
    public RateLimitType getLimitType() {
        return limitType;
    }
    
    public Duration getRetryAfter() {
        return retryAfter;
    }
}