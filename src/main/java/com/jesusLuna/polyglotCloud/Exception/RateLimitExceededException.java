package com.jesusLuna.polyglotCloud.exception;

public class RateLimitExceededException extends RuntimeException {
    
    private final String retryAfter;
    private final String rateLimitType;

    public RateLimitExceededException(String message) {
        super(message);
        this.retryAfter = null;
        this.rateLimitType = "GENERAL";
    }

    public RateLimitExceededException(String message, String retryAfter, String rateLimitType) {
        super(message);
        this.retryAfter = retryAfter;
        this.rateLimitType = rateLimitType;
    }

    public String getRetryAfter() {
        return retryAfter;
    }

    public String getRateLimitType() {
        return rateLimitType;
    }
}