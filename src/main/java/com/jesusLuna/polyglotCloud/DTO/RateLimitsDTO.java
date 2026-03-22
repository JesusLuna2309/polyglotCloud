package com.jesusLuna.polyglotCloud.dto;

public class RateLimitsDTO {
    public record RateLimitStats(
        long capacity,
        long available,
        long consumed,
        boolean isLimited
    ) {}

        public record AbuseStats(
        String identifier,
        String abuseType,
        int abuseCount,
        boolean isBlocked,
        Long blockTtlSeconds
    ) {}
}
